package dev.lightlogin.core.service;

import dev.lightlogin.core.config.RateLimitConfig;
import dev.lightlogin.core.config.SafetyConfig;
import dev.lightlogin.core.config.SecurityConfig;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.crypto.Argon2idPasswordHasher;
import dev.lightlogin.core.crypto.Pepper;
import dev.lightlogin.core.geo.CountryResolver;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AuthResult;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.policy.PasswordPolicy;
import dev.lightlogin.core.policy.PasswordPolicyConfig;
import dev.lightlogin.core.ratelimit.RateLimiterRegistry;
import dev.lightlogin.core.security.AuditAction;
import dev.lightlogin.core.security.SecretRedactor;
import dev.lightlogin.core.support.InMemoryAccountRepository;
import dev.lightlogin.core.support.InMemoryAuditRepository;
import dev.lightlogin.core.support.InMemoryIpBanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";
    private static final String USERNAME = "steve";
    private static final String IP = "203.0.113.9";

    private InMemoryAccountRepository accounts;
    private InMemoryAuditRepository auditRepo;
    private InMemoryIpBanRepository banRepo;
    private AuthService auth;

    private AuthService build(SecurityConfig security, RateLimitConfig rateLimit) {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher(Argon2Parameters.OWASP_MINIMUM, Pepper.none());
        PasswordPolicy policy = new PasswordPolicy(PasswordPolicyConfig.DEFAULT);
        AuditService audit = new AuditService(auditRepo, new SecretRedactor(true));
        IpBanService ipBans = new IpBanService(banRepo, SafetyConfig.defaults(), CountryResolver.DISABLED);
        RateLimiterRegistry limiter = new RateLimiterRegistry(rateLimit.loginBurst(), rateLimit.loginRate(),
                60_000_000_000L);
        return new AuthService(accounts, hasher, policy, limiter, ipBans, audit, security, rateLimit);
    }

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        auditRepo = new InMemoryAuditRepository();
        banRepo = new InMemoryIpBanRepository();
        auth = build(SecurityConfig.defaults(), new RateLimitConfig(1000, 1000, 1000, 1000, 100, 100, 8, 0, 20, true));
    }

    @Test
    @DisplayName("register then login succeeds")
    void registerAndLogin() {
        AuthResult registered = auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.Success.class, registered);

        AuthResult loggedIn = auth.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.Success.class, loggedIn);

        assertTrue(auditRepo.actions().contains(AuditAction.REGISTER_SUCCESS));
        assertTrue(auditRepo.actions().contains(AuditAction.LOGIN_SUCCESS));
    }

    @Test
    @DisplayName("a weak password is refused with the specific violations")
    void weakPasswordRefused() {
        AuthResult result = auth.register(UUID, USERNAME, "short".toCharArray(), IP);
        AuthResult.PolicyRejected rejected = assertInstanceOf(AuthResult.PolicyRejected.class, result);
        assertFalse(rejected.violations().isEmpty());
        assertTrue(accounts.findByUuid(UUID).isEmpty(), "no account is created for a rejected password");
    }

    @Test
    @DisplayName("a wrong password is refused and counted")
    void wrongPassword() {
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);

        AuthResult result = auth.login(UUID, USERNAME, "Wr0ng!Pass".toCharArray(), IP);
        AuthResult.WrongPassword wrong = assertInstanceOf(AuthResult.WrongPassword.class, result);
        assertEquals(SecurityConfig.defaults().maxFailedAttempts() - 1, wrong.attemptsRemaining());
        assertEquals(1, accounts.findByUuid(UUID).orElseThrow().failedAttempts());
    }

    @Test
    @DisplayName("the account locks after the configured number of failures")
    void lockout() {
        SecurityConfig security = new SecurityConfig(Argon2Parameters.OWASP_MINIMUM, "", "k", 60_000,
                3, 60_000, true, PasswordPolicyConfig.DEFAULT, 90);
        auth = build(security, new RateLimitConfig(1000, 1000, 1000, 1000, 100, 100, 8, 0, 20, true));
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);

        for (int i = 0; i < 3; i++) {
            auth.login(UUID, USERNAME, "Wr0ng!Pass".toCharArray(), IP);
        }
        AuthResult result = auth.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.Locked.class, result);
        assertTrue(auditRepo.actions().contains(AuditAction.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("login for an unknown account reports NotRegistered, not an error")
    void unknownAccount() {
        AuthResult result = auth.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.NotRegistered.class, result);
    }

    @Test
    @DisplayName("registering twice reports AlreadyRegistered")
    void duplicateRegistration() {
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.AlreadyRegistered.class,
                auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));
    }

    @Test
    @DisplayName("the per-IP registration limit is enforced")
    void perIpRegistrationLimit() {
        // Limit of 1 registration per IP.
        AuthService limited = build(SecurityConfig.defaults(),
                new RateLimitConfig(1000, 1000, 1000, 1000, 100, 100, 8, 0, 20, true));
        assertInstanceOf(AuthResult.Success.class,
                limited.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));

        AuthResult second = limited.register("22222222-2222-2222-2222-222222222222", "alex",
                "Str0ng!Pass".toCharArray(), IP);
        // The default SafetyConfig allows 2 per IP, so a third registration trips the limit.
        assertInstanceOf(AuthResult.Success.class, second);

        AuthResult third = limited.register("33333333-3333-3333-3333-333333333333", "notch",
                "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.RegistrationLimitReached.class, third);
    }

    @Test
    @DisplayName("a banned IP cannot register or log in")
    void ipBanEnforced() {
        banRepo.save(IpBan.permanent(IP, "abuse", "admin", System.currentTimeMillis(), IpBan.BanSource.MANUAL));
        assertInstanceOf(AuthResult.IpBanned.class,
                auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));
        assertInstanceOf(AuthResult.IpBanned.class,
                auth.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));
    }

    @Test
    @DisplayName("login is rate limited per IP")
    void loginRateLimited() {
        AuthService tight = build(SecurityConfig.defaults(),
                new RateLimitConfig(8, 1.5, 2, 0.001, 6, 2, 8, 0, 20, true));
        tight.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);

        assertInstanceOf(AuthResult.Success.class, tight.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));
        assertInstanceOf(AuthResult.WrongPassword.class, tight.login(UUID, USERNAME, "x".toCharArray(), IP));
        AuthResult third = tight.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        assertInstanceOf(AuthResult.RateLimited.class, third);
    }

    @Test
    @DisplayName("changing a password verifies the old one and enforces the policy")
    void changePassword() {
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);

        assertInstanceOf(AuthResult.WrongPassword.class,
                auth.changePassword(UUID, USERNAME, "Wr0ng!Pass".toCharArray(), "N3w!Passw0rd".toCharArray(), IP));
        assertInstanceOf(AuthResult.PolicyRejected.class,
                auth.changePassword(UUID, USERNAME, "Str0ng!Pass".toCharArray(), "weak".toCharArray(), IP));
        assertInstanceOf(AuthResult.Success.class,
                auth.changePassword(UUID, USERNAME, "Str0ng!Pass".toCharArray(), "N3w!Passw0rd".toCharArray(), IP));

        assertInstanceOf(AuthResult.Success.class,
                auth.login(UUID, USERNAME, "N3w!Passw0rd".toCharArray(), IP));
    }

    @Test
    @DisplayName("unregistering removes the account")
    void unregister() {
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        auth.unregister(UUID, USERNAME, "admin", IP);
        assertTrue(accounts.findByUuid(UUID).isEmpty());
        assertTrue(auditRepo.actions().contains(AuditAction.ACCOUNT_UNREGISTERED));
    }

    @Test
    @DisplayName("a stored hash is upgraded transparently when parameters are raised")
    void transparentRehash() {
        // Register with a weaker hasher, then log in with a stronger one.
        Argon2idPasswordHasher weak = new Argon2idPasswordHasher(Argon2Parameters.OWASP_MINIMUM, Pepper.none());
        String weakHash = weak.hash("Str0ng!Pass".toCharArray());
        accounts.save(Account.create(UUID, USERNAME, weakHash, null, IP, System.currentTimeMillis()));

        Argon2idPasswordHasher strong = new Argon2idPasswordHasher(Argon2Parameters.BALANCED, Pepper.none());
        AuditService audit = new AuditService(auditRepo, new SecretRedactor(true));
        AuthService service = new AuthService(accounts, strong, new PasswordPolicy(PasswordPolicyConfig.DEFAULT),
                new RateLimiterRegistry(1000, 1000, 60_000_000_000L),
                new IpBanService(banRepo, SafetyConfig.defaults(), CountryResolver.DISABLED),
                audit, SecurityConfig.defaults(),
                new RateLimitConfig(1000, 1000, 1000, 1000, 100, 100, 8, 0, 20, true));

        AuthResult.Success success = assertInstanceOf(AuthResult.Success.class,
                service.login(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP));
        assertTrue(success.passwordUpgraded());
        assertFalse(strong.needsRehash(accounts.findByUuid(UUID).orElseThrow().passwordHash()));
    }

    @Test
    @DisplayName("no plaintext password is ever written to the audit trail")
    void auditNeverContainsPlaintext() {
        auth.register(UUID, USERNAME, "Str0ng!Pass".toCharArray(), IP);
        auth.login(UUID, USERNAME, "Wr0ng!Pass".toCharArray(), IP);

        for (var entry : auditRepo.all()) {
            assertFalse(entry.detail().contains("Str0ng!Pass"));
            assertFalse(entry.detail().contains("Wr0ng!Pass"));
        }
        assertTrue(Set.copyOf(auditRepo.actions()).containsAll(List.of(
                AuditAction.REGISTER_SUCCESS, AuditAction.LOGIN_FAILURE)));
    }
}