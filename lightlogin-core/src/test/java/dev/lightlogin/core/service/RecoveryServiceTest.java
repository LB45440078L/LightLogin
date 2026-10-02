package dev.lightlogin.core.service;

import dev.lightlogin.core.config.MailConfig;
import dev.lightlogin.core.config.SecurityConfig;
import dev.lightlogin.core.crypto.Argon2Parameters;
import dev.lightlogin.core.crypto.Argon2idPasswordHasher;
import dev.lightlogin.core.crypto.Pepper;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.security.SecretRedactor;
import dev.lightlogin.core.support.InMemoryAccountRepository;
import dev.lightlogin.core.support.InMemoryAuditRepository;
import dev.lightlogin.core.support.InMemorySessionRepository;
import dev.lightlogin.core.support.RecordingMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryServiceTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";
    private static final String USERNAME = "steve";

    private InMemoryAccountRepository accounts;
    private InMemorySessionRepository sessions;
    private RecordingMailSender mail;
    private Argon2idPasswordHasher hasher;
    private RecoveryService recovery;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        sessions = new InMemorySessionRepository();
        mail = new RecordingMailSender();
        hasher = new Argon2idPasswordHasher(Argon2Parameters.OWASP_MINIMUM, Pepper.none());
        SecurityConfig security = SecurityConfig.defaults();
        sessionService = new SessionService(sessions, new TokenGenerator(), security);
        MailConfig mailConfig = new MailConfig(true, "smtp.example.com", 587, true, "noreply@example.com",
                "", "LightLogin", "Your new password", List.of("Hi {PLAYER}", "Password: {PASSWORD}"),
                12, 90 * 60 * 1000L, 15_000);
        recovery = new RecoveryService(accounts, hasher, sessionService, mail, mailConfig,
                new TokenGenerator(), new AuditService(new InMemoryAuditRepository(), new SecretRedactor(true)));
    }

    private void registerWithEmail(String email) {
        accounts.save(dev.lightlogin.core.model.Account.create(UUID, USERNAME,
                hasher.hash("Str0ng!Pass".toCharArray()), email, "1.2.3.4", System.currentTimeMillis()));
    }

    @Test
    @DisplayName("a reset emails a temporary password that actually works")
    void resetSendsWorkingPassword() {
        registerWithEmail("steve@example.com");
        String existingSession = sessionService.create(UUID, "1.2.3.4");

        RecoveryService.Outcome outcome = recovery.requestReset(USERNAME, USERNAME, "1.2.3.4");
        assertEquals(RecoveryService.Outcome.DISPATCHED, outcome);
        assertEquals(1, mail.sent().size());

        String body = mail.last().body();
        String temporary = body.lines().filter(l -> l.startsWith("Password: "))
                .map(l -> l.substring("Password: ".length()).trim()).findFirst().orElseThrow();

        assertTrue(hasher.verify(temporary.toCharArray(), accounts.findByUuid(UUID).orElseThrow().passwordHash()));
        // Existing sessions are invalidated by a reset.
        assertTrue(sessionService.validate(existingSession, "1.2.3.4").isEmpty());
    }

    @Test
    @DisplayName("the cooldown prevents a second immediate reset")
    void cooldown() {
        registerWithEmail("steve@example.com");
        assertEquals(RecoveryService.Outcome.DISPATCHED, recovery.requestReset(USERNAME, USERNAME, "1.2.3.4"));
        assertEquals(RecoveryService.Outcome.COOLDOWN, recovery.requestReset(USERNAME, USERNAME, "1.2.3.4"));
        assertEquals(1, mail.sent().size());
    }

    @Test
    @DisplayName("an unknown account does not leak its existence")
    void unknownAccount() {
        assertEquals(RecoveryService.Outcome.DISPATCHED, recovery.requestReset("nobody", "nobody", "1.2.3.4"));
        assertTrue(mail.sent().isEmpty());
    }

    @Test
    @DisplayName("an account without an email reports NO_EMAIL")
    void noEmail() {
        registerWithEmail(null);
        assertEquals(RecoveryService.Outcome.NO_EMAIL, recovery.requestReset(USERNAME, USERNAME, "1.2.3.4"));
    }

    @Test
    @DisplayName("a delivery failure is reported and does not crash")
    void deliveryFailure() {
        registerWithEmail("steve@example.com");
        mail.failNextSend();
        assertEquals(RecoveryService.Outcome.FAILED, recovery.requestReset(USERNAME, USERNAME, "1.2.3.4"));
    }

    @Test
    @DisplayName("the temporary password is high-entropy and changes each time")
    void distinctPasswords() {
        registerWithEmail("steve@example.com");
        recovery.requestReset(USERNAME, USERNAME, "1.2.3.4");
        String first = mail.last().body();
        // A second account, to bypass the cooldown.
        accounts.save(dev.lightlogin.core.model.Account.create("22222222-2222-2222-2222-222222222222",
                "alex", hasher.hash("Str0ng!Pass".toCharArray()), "alex@example.com", "5.6.7.8",
                System.currentTimeMillis()));
        recovery.requestReset("alex", "alex", "5.6.7.8");
        String second = mail.last().body();
        assertNotEquals(first, second);
        assertFalse(second.contains("Str0ng!Pass"));
    }
}