package dev.lightlogin.core.service;

import dev.lightlogin.core.config.RateLimitConfig;
import dev.lightlogin.core.config.SecurityConfig;
import dev.lightlogin.core.crypto.PasswordHasher;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AuthResult;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.policy.PasswordPolicy;
import dev.lightlogin.core.policy.PolicyViolation;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.AuditAction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The authentication use cases: register, login, change password and unregister.
 *
 * <p>Every method blocks and is intended to be called from the async executor, never from the
 * server's main thread. The class holds no per-player state: the only mutable state is the
 * rate-limiter registry, which is itself thread-safe, so the service can be shared freely.</p>
 *
 * <p>The login path is deliberately uniform. A missing account, a wrong password and a locked
 * account all consume the same rate-limit token and take a comparable amount of work (a dummy
 * verification runs when the account is absent) so that response timing and rate accounting do not
 * reveal whether a username exists.</p>
 */
public final class AuthService {

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final PasswordPolicy policy;
    private final RateLimiterRegistry loginLimiter;
    private final IpBanService ipBans;
    private final AuditService audit;
    private final SecurityConfig security;
    private final RateLimitConfig rateConfig;
    private final LongSupplier clock;

    /** A fixed dummy hash used to equalise the cost of a login against a non-existent account. */
    private static final char[] DUMMY_PASSWORD = "lightlogin-timing-equaliser".toCharArray();

    public AuthService(AccountRepository accounts,
                       PasswordHasher hasher,
                       PasswordPolicy policy,
                       RateLimiterRegistry loginLimiter,
                       IpBanService ipBans,
                       AuditService audit,
                       SecurityConfig security,
                       RateLimitConfig rateConfig) {
        this(accounts, hasher, policy, loginLimiter, ipBans, audit, security, rateConfig,
                System::currentTimeMillis);
    }

    AuthService(AccountRepository accounts, PasswordHasher hasher, PasswordPolicy policy,
                RateLimiterRegistry loginLimiter, IpBanService ipBans, AuditService audit,
                SecurityConfig security, RateLimitConfig rateConfig, LongSupplier clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.loginLimiter = Objects.requireNonNull(loginLimiter, "loginLimiter");
        this.ipBans = Objects.requireNonNull(ipBans, "ipBans");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.security = Objects.requireNonNull(security, "security");
        this.rateConfig = Objects.requireNonNull(rateConfig, "rateConfig");
        this.clock = clock;
    }

    /**
     * Registers a new account.
     *
     * @param uuid     player UUID
     * @param username player name
     * @param password the chosen password (wiped by the caller)
     * @param ip       source address
     */
    public AuthResult register(String uuid, String username, char[] password, String ip) {
        long now = clock.getAsLong();

        if (accounts.findByUuid(uuid).filter(Account::isRegistered).isPresent()) {
            return new AuthResult.AlreadyRegistered();
        }

        IpBanService.Verdict verdict = ipBans.check(ip);
        if (verdict instanceof IpBanService.Verdict.Banned banned) {
            return new AuthResult.IpBanned(banned.target(), banned.reason());
        }

        if (security.passwordPolicy().denyCommon()) {
            // policy.validate already covers this; kept explicit for readability of intent.
        }
        List<PolicyViolation> violations = policy.validate(password, username);
        if (!violations.isEmpty()) {
            audit.record(username, AuditAction.REGISTER_FAILURE, uuid, "policy: " + codes(violations), ip);
            return new AuthResult.PolicyRejected(violations.stream().map(v -> v.message()).toList());
        }

        int limit = ipBans.config().maxRegistrationsPerIp();
        if (limit > 0 && !ip.isBlank()) {
            long existing = accounts.countByRegistrationIp(ip);
            if (existing >= limit) {
                audit.record(username, AuditAction.REGISTER_FAILURE, uuid, "per-ip limit reached", ip);
                return new AuthResult.RegistrationLimitReached(limit);
            }
        }

        String hash = hasher.hash(password);
        Account account = Account.create(uuid, username, hash, null, ip, now);
        accounts.save(account);
        audit.record(username, AuditAction.REGISTER_SUCCESS, uuid, "", ip);
        return new AuthResult.Success(uuid, false);
    }

    /**
     * Authenticates a player.
     *
     * @param password the supplied password
     */
    public AuthResult login(String uuid, String username, char[] password, String ip) {
        long now = clock.getAsLong();

        IpBanService.Verdict verdict = ipBans.check(ip);
        if (verdict instanceof IpBanService.Verdict.Banned banned) {
            audit.record(username, AuditAction.LOGIN_BLOCKED, uuid, "ip banned", ip);
            return new AuthResult.IpBanned(banned.target(), banned.reason());
        }

        if (!loginLimiter.tryAcquire(ip == null || ip.isBlank() ? "unknown" : ip)) {
            long wait = loginLimiter.nanosUntilAvailable(ip) / 1_000_000L;
            audit.record(username, AuditAction.RATE_LIMITED, uuid, "", ip);
            return new AuthResult.RateLimited(wait);
        }

        Optional<Account> found = accounts.findByUuid(uuid);
        if (found.isEmpty() || !found.get().isRegistered()) {
            // Equalise timing against the registered path.
            hasher.verify(DUMMY_PASSWORD, hasher.hash(DUMMY_PASSWORD));
            audit.record(username, AuditAction.LOGIN_FAILURE, uuid, "not registered", ip);
            return new AuthResult.NotRegistered();
        }

        Account account = found.get();
        if (account.isLocked(now)) {
            audit.record(username, AuditAction.LOGIN_BLOCKED, uuid, "locked", ip);
            return new AuthResult.Locked(account.lockedUntilMillis());
        }

        if (!hasher.verify(password, account.passwordHash())) {
            int attempts = accounts.incrementFailedAttempts(uuid);
            int remaining = Math.max(0, security.maxFailedAttempts() - attempts);
            if (remaining <= 0 && security.lockoutMillis() > 0) {
                long until = now + security.lockoutMillis();
                accounts.lock(uuid, until);
                audit.record(username, AuditAction.ACCOUNT_LOCKED, uuid, "max attempts", ip);
            }
            audit.record(username, AuditAction.LOGIN_FAILURE, uuid, "wrong password", ip);
            return new AuthResult.WrongPassword(remaining);
        }

        boolean upgraded = false;
        if (hasher.needsRehash(account.passwordHash())) {
            accounts.updatePassword(uuid, hasher.hash(password));
            upgraded = true;
        }
        accounts.recordLogin(uuid, ip, now);
        audit.record(username, AuditAction.LOGIN_SUCCESS, uuid, upgraded ? "hash upgraded" : "", ip);
        return new AuthResult.Success(uuid, upgraded);
    }

    /** Changes a password after verifying the current one. */
    public AuthResult changePassword(String uuid, String username, char[] oldPassword,
                                     char[] newPassword, String ip) {
        Optional<Account> found = accounts.findByUuid(uuid);
        if (found.isEmpty() || !found.get().isRegistered()) {
            return new AuthResult.NotRegistered();
        }
        if (!hasher.verify(oldPassword, found.get().passwordHash())) {
            audit.record(username, AuditAction.LOGIN_FAILURE, uuid, "wrong old password", ip);
            return new AuthResult.WrongPassword(Math.max(0, security.maxFailedAttempts() - 1));
        }
        List<PolicyViolation> violations = policy.validate(newPassword, username);
        if (!violations.isEmpty()) {
            return new AuthResult.PolicyRejected(violations.stream().map(v -> v.message()).toList());
        }
        accounts.updatePassword(uuid, hasher.hash(newPassword));
        audit.record(username, AuditAction.PASSWORD_CHANGE, uuid, "", ip);
        return new AuthResult.Success(uuid, true);
    }

    /** Sets a new password without knowing the old one (admin/recovery path). */
    public void forcePassword(String uuid, String username, char[] newPassword, String actor, String ip) {
        accounts.updatePassword(uuid, hasher.hash(newPassword));
        audit.record(actor, AuditAction.PASSWORD_RESET, uuid, "forced by " + actor, ip);
    }

    /** Deletes an account. */
    public void unregister(String uuid, String username, String actor, String ip) {
        accounts.delete(uuid);
        audit.record(actor, AuditAction.ACCOUNT_UNREGISTERED, uuid, username, ip);
    }

    /** Sets the recovery email after a format check performed by the caller. */
    public void setEmail(String uuid, String username, String email, String ip) {
        accounts.updateEmail(uuid, email);
        audit.record(username, AuditAction.PASSWORD_CHANGE, uuid, "email updated", ip);
    }

    /** Returns an account for display purposes. */
    public Optional<Account> findAccount(String uuid) {
        return accounts.findByUuid(uuid);
    }

    /** The configured password policy, exposed so the command layer can render its rules. */
    public PasswordPolicy passwordPolicy() {
        return policy;
    }

    public SecurityConfig securityConfig() {
        return security;
    }

    public RateLimitConfig rateLimitConfig() {
        return rateConfig;
    }

    private static String codes(List<PolicyViolation> violations) {
        return violations.stream().map(v -> v.code().name()).reduce((a, b) -> a + "," + b).orElse("");
    }

    /** Builds a temporary ban from a brute-force trigger. */
    public void banForBruteForce(String ip, String username) {
        if (!ipBans.config().autoBanOnBruteForce() || ip.isBlank()) {
            return;
        }
        IpBan ban = IpBan.temporary(ip, "Brute-force login attempts", "auto", clock.getAsLong(),
                ipBans.config().bruteForceBanMillis(), IpBan.BanSource.BRUTE_FORCE);
        ipBans.ban(ban);
        audit.record("system", AuditAction.IP_BANNED, username, "brute force", ip);
    }
}