package dev.lightlogin.core.web;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.port.AuditRepository;
import dev.lightlogin.core.port.IpBanRepository;
import dev.lightlogin.core.port.SessionRepository;
import dev.lightlogin.core.crypto.PasswordHasher;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.security.AccessToken;
import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.core.security.SecurityContext;
import dev.lightlogin.core.service.AuditService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The privileged operations behind the administration panel.
 *
 * <p>Every method that can change state takes an {@link AccessToken} and verifies it against the
 * {@link SecurityContext} before doing anything. The token is only held by the panel's own
 * components, so another plugin that reaches this class by reflection still cannot invoke a
 * destructive action without first reading the token out of the running JVM — and every rejected
 * call is counted and audited.</p>
 */
public final class AdminService {

    private final SecurityContext security;
    private final AccountRepository accounts;
    private final IpBanRepository bans;
    private final AuditRepository auditRepository;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final TokenGenerator tokens;
    private final AuditService audit;

    public AdminService(SecurityContext security,
                        AccountRepository accounts,
                        IpBanRepository bans,
                        AuditRepository auditRepository,
                        SessionRepository sessions,
                        PasswordHasher hasher,
                        TokenGenerator tokens,
                        AuditService audit) {
        this.security = Objects.requireNonNull(security, "security");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.bans = Objects.requireNonNull(bans, "bans");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /** Aggregate counters for the dashboard. */
    public record Stats(long accounts, long registeredAccounts, long bans, long activeBans,
                        long sessions, long auditEntries) {
    }

    public Stats stats(AccessToken token) {
        security.require(token);
        long now = System.currentTimeMillis();
        long total = accounts.count();
        List<IpBan> allBans = bans.all();
        return new Stats(total, total, allBans.size(),
                allBans.stream().filter(b -> b.isActive(now)).count(),
                sessions.count(), auditRepository.count());
    }

    public List<Account> players(AccessToken token, int offset, int limit) {
        security.require(token);
        return accounts.page(offset, limit);
    }

    public List<Account> searchPlayers(AccessToken token, String query, int offset, int limit) {
        security.require(token);
        return accounts.search(query, offset, limit);
    }

    public Optional<Account> player(AccessToken token, String uuid) {
        security.require(token);
        return accounts.findByUuid(uuid);
    }

    public List<IpBan> bans(AccessToken token) {
        security.require(token);
        return bans.all();
    }

    public List<AuditEntry> logs(AccessToken token, int offset, int limit) {
        security.require(token);
        return auditRepository.page(offset, limit);
    }

    public List<AuditEntry> logsFor(AccessToken token, String subject, int limit) {
        security.require(token);
        return auditRepository.forSubject(subject, limit);
    }

    /** Resets a player's password to a fresh temporary one and returns it once. */
    public Optional<String> resetPassword(AccessToken token, String uuid, String actor) {
        security.require(token);
        Optional<Account> account = accounts.findByUuid(uuid);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        String temporary = tokens.temporaryPassword(12);
        char[] chars = temporary.toCharArray();
        try {
            accounts.updatePassword(uuid, hasher.hash(chars));
        } finally {
            dev.lightlogin.core.crypto.ConstantTime.wipe(chars);
        }
        sessions.deleteByUuid(uuid);
        audit.record(actor, AuditAction.PASSWORD_RESET, uuid, "panel reset", "");
        return Optional.of(temporary);
    }

    public void unregister(AccessToken token, String uuid, String actor) {
        security.require(token);
        accounts.delete(uuid);
        sessions.deleteByUuid(uuid);
        audit.record(actor, AuditAction.ACCOUNT_UNREGISTERED, uuid, "panel action", "");
    }

    public void ban(AccessToken token, String target, String reason, long durationMillis, String actor) {
        security.require(token);
        long now = System.currentTimeMillis();
        IpBan ban = durationMillis <= 0
                ? IpBan.permanent(target, reason, actor, now, IpBan.BanSource.MANUAL)
                : IpBan.temporary(target, reason, actor, now, durationMillis, IpBan.BanSource.MANUAL);
        bans.save(ban);
        audit.record(actor, AuditAction.IP_BANNED, target, reason, "");
    }

    public boolean unban(AccessToken token, String target, String actor) {
        security.require(token);
        boolean existed = bans.findByTarget(target).isPresent();
        if (existed) {
            bans.deleteByTarget(target);
            audit.record(actor, AuditAction.IP_UNBANNED, target, "panel action", "");
        }
        return existed;
    }

    /** Role check helper for the panel. */
    public static boolean allows(AdminRole role, AdminRole required) {
        return role != null && role.atLeast(required);
    }
}