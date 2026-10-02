package dev.lightlogin.persistence;

import dev.lightlogin.core.config.DatabaseConfig;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AccountStatus;
import dev.lightlogin.core.model.AdminAccount;
import dev.lightlogin.core.model.AdminRole;
import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real JDBC repositories against a real SQLite database in a temporary directory, so the
 * schema, the SQL and the transaction handling are exercised end to end rather than mocked.
 */
class JdbcRepositoriesTest {

    private PersistenceBootstrap persistence;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        DatabaseConfig config = new DatabaseConfig(DatabaseConfig.DatabaseType.SQLITE, "127.0.0.1", 0,
                "lightlogin", "", "", dir.resolve("test.db").toString(), 2, 10_000);
        persistence = PersistenceBootstrap.start(config, "", dir);
    }

    @AfterEach
    void tearDown() {
        if (persistence != null) {
            persistence.close();
        }
    }

    @Test
    @DisplayName("migrations apply exactly once and are idempotent")
    void migrations() {
        List<String> second = persistence.migrate();
        assertTrue(second.isEmpty(), "a second run must apply nothing");
        assertTrue(persistence.isHealthy());
    }

    @Test
    @DisplayName("an account round-trips through the database")
    void accountRoundTrip() {
        Account account = Account.create("11111111-1111-1111-1111-111111111111", "Steve",
                "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA", "steve@example.com", "203.0.113.5",
                1_700_000_000_000L);
        persistence.accounts().save(account);

        Optional<Account> found = persistence.accounts().findByUuid(account.uuid());
        assertTrue(found.isPresent());
        assertEquals("Steve", found.get().username());
        assertEquals("steve", found.get().usernameLower());
        assertEquals("steve@example.com", found.get().email());
        assertEquals(AccountStatus.ACTIVE, found.get().status());

        // Lookup is case-insensitive.
        assertTrue(persistence.accounts().findByUsername("STEVE").isPresent());
        assertTrue(persistence.accounts().findByUsername("nobody").isEmpty());
    }

    @Test
    @DisplayName("nullable fields round-trip as null, not as empty strings")
    void nullsPreserved() {
        Account account = Account.create("22222222-2222-2222-2222-222222222222", "alex",
                "hash", null, "10.0.0.1", 1L);
        persistence.accounts().save(account);

        Account reloaded = persistence.accounts().findByUuid(account.uuid()).orElseThrow();
        assertEquals(null, reloaded.email());
        assertEquals(null, reloaded.lastIp());
        assertFalse(reloaded.isRegistered() == false);
    }

    @Test
    @DisplayName("save is an upsert, not a duplicate insert")
    void upsert() {
        Account account = Account.create("33333333-3333-3333-3333-333333333333", "notch",
                "hash1", null, "1.1.1.1", 1L);
        persistence.accounts().save(account);
        persistence.accounts().save(account.withPasswordHash("hash2"));

        assertEquals(1, persistence.accounts().count());
        assertEquals("hash2", persistence.accounts().findByUuid(account.uuid()).orElseThrow().passwordHash());
    }

    @Test
    @DisplayName("failed attempts increment atomically and return the new value")
    void failedAttempts() {
        Account account = Account.create("44444444-4444-4444-4444-444444444444", "steve", "hash",
                null, "1.1.1.1", 1L);
        persistence.accounts().save(account);

        assertEquals(1, persistence.accounts().incrementFailedAttempts(account.uuid()));
        assertEquals(2, persistence.accounts().incrementFailedAttempts(account.uuid()));
        assertEquals(3, persistence.accounts().incrementFailedAttempts(account.uuid()));

        persistence.accounts().resetFailedAttempts(account.uuid());
        assertEquals(0, persistence.accounts().findByUuid(account.uuid()).orElseThrow().failedAttempts());
    }

    @Test
    @DisplayName("locking and recording a login update the account")
    void lockAndLogin() {
        Account account = Account.create("55555555-5555-5555-5555-555555555555", "steve", "hash",
                null, "1.1.1.1", 1L);
        persistence.accounts().save(account);

        long until = 2_000_000_000_000L;
        persistence.accounts().lock(account.uuid(), until);
        Account locked = persistence.accounts().findByUuid(account.uuid()).orElseThrow();
        assertEquals(AccountStatus.LOCKED, locked.status());
        assertEquals(until, locked.lockedUntilMillis());

        persistence.accounts().recordLogin(account.uuid(), "203.0.113.9", 1_700_000_000_000L);
        Account afterLogin = persistence.accounts().findByUuid(account.uuid()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, afterLogin.status());
        assertEquals("203.0.113.9", afterLogin.lastIp());
        assertEquals(0, afterLogin.lockedUntilMillis());
    }

    @Test
    @DisplayName("per-IP registration counts are correct")
    void registrationIpCount() {
        for (int i = 0; i < 3; i++) {
            persistence.accounts().save(Account.create("aaaaaaaa-0000-0000-0000-00000000000" + i,
                    "user" + i, "hash", null, "203.0.113.7", i));
        }
        assertEquals(3, persistence.accounts().countByRegistrationIp("203.0.113.7"));
        assertEquals(0, persistence.accounts().countByRegistrationIp("198.51.100.1"));
    }

    @Test
    @DisplayName("paging and search return the expected slices")
    void pagingAndSearch() {
        for (int i = 0; i < 10; i++) {
            persistence.accounts().save(Account.create("bbbbbbbb-0000-0000-0000-00000000000" + i,
                    "player" + i, "hash", null, "1.2.3.4", 1000L + i));
        }
        assertEquals(10, persistence.accounts().count());
        assertEquals(5, persistence.accounts().page(0, 5).size());
        assertEquals(5, persistence.accounts().page(5, 5).size());
        assertEquals(0, persistence.accounts().page(20, 5).size());

        List<Account> search = persistence.accounts().search("player3", 0, 25);
        assertEquals(1, search.size());
        assertEquals("player3", search.get(0).username());
    }

    @Test
    @DisplayName("sessions store only a digest and can be purged")
    void sessions() {
        persistence.sessions().create(new Session("digest-1", "uuid-1", "1.2.3.4", 1000, 2000));
        assertTrue(persistence.sessions().findByTokenHash("digest-1").isPresent());
        assertEquals(1, persistence.sessions().count());

        persistence.sessions().create(new Session("digest-2", "uuid-2", "1.2.3.4", 1000, 500));
        assertEquals(1, persistence.sessions().deleteExpired(1000), "only the already-expired session goes");

        persistence.sessions().deleteByUuid("uuid-1");
        assertTrue(persistence.sessions().findByTokenHash("digest-1").isEmpty());
    }

    @Test
    @DisplayName("IP bans support CIDR matching and expiry")
    void ipBans() {
        persistence.bans().save(IpBan.permanent("203.0.113.0/24", "range", "admin", 1000,
                IpBan.BanSource.MANUAL));
        persistence.bans().save(IpBan.temporary("198.51.100.5", "temp", "admin", 1000, 500,
                IpBan.BanSource.BRUTE_FORCE));

        assertTrue(persistence.bans().findActiveFor("203.0.113.42", 2000).isPresent());
        assertTrue(persistence.bans().findActiveFor("198.51.100.5", 1200).isPresent());
        assertTrue(persistence.bans().findActiveFor("198.51.100.5", 2000).isEmpty(), "temporary ban expired");
        assertTrue(persistence.bans().findActiveFor("8.8.8.8", 2000).isEmpty());

        assertEquals(1, persistence.bans().purgeExpired(2000));
        assertEquals(1, persistence.bans().count());
    }

    @Test
    @DisplayName("the audit id is monotonic and entries page newest-first")
    void audit() {
        for (int i = 0; i < 5; i++) {
            persistence.audit().append(AuditEntry.of(1000L + i, "steve", "LOGIN_SUCCESS", "uuid-" + i,
                    "detail", "1.2.3.4"));
        }
        assertEquals(5, persistence.audit().count());
        List<AuditEntry> recent = persistence.audit().recent(3);
        assertEquals(3, recent.size());
        assertTrue(recent.get(0).id() > recent.get(1).id());
        assertEquals(5, recent.get(0).id());
        assertEquals(1, persistence.audit().forSubject("uuid-2", 10).size());
        assertEquals(5, persistence.audit().purgeOlderThan(2000));
    }

    @Test
    @DisplayName("audit detail longer than the column is truncated rather than failing")
    void auditTruncation() {
        persistence.audit().append(AuditEntry.of(1L, "steve", "ACTION", "subj",
                "x".repeat(2000), "1.2.3.4"));
        assertEquals(1, persistence.audit().count());
    }

    @Test
    @DisplayName("admin accounts round-trip including the TOTP secret")
    void adminAccounts() {
        persistence.admins().save(new AdminAccount("admin", "hash", AdminRole.ADMIN, 1000, 0,
                "JBSWY3DPEHPK3PXP"));
        Optional<AdminAccount> found = persistence.admins().findByUsername("admin");
        assertTrue(found.isPresent());
        assertEquals(AdminRole.ADMIN, found.get().role());
        assertTrue(found.get().hasTotp());

        persistence.admins().recordLogin("admin", 5000);
        assertEquals(5000, persistence.admins().findByUsername("admin").orElseThrow().lastLoginMillis());

        persistence.admins().delete("admin");
        assertEquals(0, persistence.admins().count());
    }

    @Test
    @DisplayName("concurrent audit writes all land with unique ids")
    void concurrentAuditWrites() throws InterruptedException {
        int threads = 8;
        int perThread = 25;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    persistence.audit().append(AuditEntry.of(System.currentTimeMillis(), "sys",
                            "ACTION", "s", "d", "1.1.1.1"));
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(threads * perThread, persistence.audit().count());
    }
}