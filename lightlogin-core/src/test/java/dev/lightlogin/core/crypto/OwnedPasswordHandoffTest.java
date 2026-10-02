package dev.lightlogin.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the password handoff.
 *
 * <p>The bug these exist for: the async task used to copy the password <em>inside</em> the task,
 * while the caller wiped the array immediately after submitting. The task then usually hashed an
 * all-zero array, so a registered password never verified. These tests drive the handoff across a
 * real thread boundary, which is where the old code failed.</p>
 */
class OwnedPasswordHandoffTest {

    private static final String PASSWORD = "MarcoCampari2003!";

    @Test
    @DisplayName("claim copies before wiping, so the value survives the caller wiping its array")
    void claimCopiesBeforeWiping() {
        char[] source = PASSWORD.toCharArray();
        OwnedPassword owned = OwnedPassword.claim(source);

        // The caller's array is gone immediately...
        for (char c : source) {
            assertEquals(0, c, "the caller's array must be wiped by claim()");
        }
        // ...but the value is intact.
        assertEquals(PASSWORD, owned.use(String::new));
    }

    @Test
    @DisplayName("the value is still correct when read on a different thread after the caller returned")
    void survivesThreadHandoff() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            char[] source = PASSWORD.toCharArray();
            OwnedPassword owned = OwnedPassword.claim(source);

            // Simulates the real pattern: submitted, the method returns, the task runs later.
            var future = executor.submit(() -> owned.use(String::new));

            assertEquals(PASSWORD, future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("use wipes the private copy afterwards")
    void useWipesAfterwards() {
        OwnedPassword owned = OwnedPassword.claim(PASSWORD.toCharArray());
        char[][] seen = new char[1][];
        owned.use(password -> {
            seen[0] = password;
            assertNotEquals(0, password[0]);
            return null;
        });
        assertTrue(seen[0] != null);
        for (char c : seen[0]) {
            assertEquals(0, c, "the private copy must be wiped after use");
        }
    }

    @Test
    @DisplayName("the value is wiped even when the work throws")
    void wipesOnFailure() {
        OwnedPassword owned = OwnedPassword.claim(PASSWORD.toCharArray());
        char[][] seen = new char[1][];
        try {
            owned.use(password -> {
                seen[0] = password;
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // ignored
        }
        for (char c : seen[0]) {
            assertEquals(0, c, "the private copy must be wiped even when the work throws");
        }
    }

    @Test
    @DisplayName("destroy wipes without running any work")
    void destroy() {
        OwnedPassword owned = OwnedPassword.claim(PASSWORD.toCharArray());
        owned.destroy();
        // A subsequent use sees the wiped value, proving destroy cleared it.
        assertEquals(String.valueOf(new char[PASSWORD.length()]), owned.use(String::new));
    }

    @Test
    @DisplayName("the password is never rendered")
    void neverRendered() {
        OwnedPassword owned = OwnedPassword.claim(PASSWORD.toCharArray());
        assertEquals("OwnedPassword[redacted]", owned.toString());
        owned.destroy();
    }

    @Test
    @DisplayName("a hasher receives the real password through the handoff, and the hash verifies")
    @Timeout(60)
    void endToEndThroughARealHasher() throws Exception {
        Argon2idPasswordHasher hasher =
                new Argon2idPasswordHasher(Argon2Parameters.OWASP_MINIMUM, Pepper.none());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // This mirrors exactly what the command layer now does.
            OwnedPassword owned = OwnedPassword.claim(PASSWORD.toCharArray());
            var future = executor.submit(() -> owned.use(hasher::hash));
            String encoded = future.get(30, TimeUnit.SECONDS);

            // And what the login path does.
            OwnedPassword forVerification = OwnedPassword.claim(PASSWORD.toCharArray());
            var verified = executor.submit(() ->
                    forVerification.use(password -> hasher.verify(password, encoded)));
            assertTrue(verified.get(30, TimeUnit.SECONDS),
                    "the password must verify after crossing the handoff");
        } finally {
            executor.shutdownNow();
        }
    }
}