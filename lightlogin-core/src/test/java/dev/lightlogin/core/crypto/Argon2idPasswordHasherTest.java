package dev.lightlogin.core.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2idPasswordHasherTest {

    private static final Argon2Parameters FAST = Argon2Parameters.OWASP_MINIMUM;

    private final Argon2idPasswordHasher hasher =
            new Argon2idPasswordHasher(FAST, Pepper.none());

    @Test
    @DisplayName("a hashed password verifies and a wrong password does not")
    void roundTrip() {
        String encoded = hasher.hash("Correct-Horse-9!".toCharArray());

        assertTrue(hasher.verify("Correct-Horse-9!".toCharArray(), encoded));
        assertFalse(hasher.verify("correct-horse-9!".toCharArray(), encoded));
        assertFalse(hasher.verify("".toCharArray(), encoded));
    }

    @Test
    @DisplayName("the salt is embedded in the encoded hash so it is never lost")
    void saltIsSelfContained() {
        String a = hasher.hash("same-password".toCharArray());
        String b = hasher.hash("same-password".toCharArray());

        // Same password must produce different hashes (random per-hash salt)...
        assertNotEquals(a, b);
        // ...yet both verify, proving the salt travels with the digest.
        assertTrue(hasher.verify("same-password".toCharArray(), a));
        assertTrue(hasher.verify("same-password".toCharArray(), b));

        PhcFormat.Argon2Hash decoded = PhcFormat.decode(a);
        assertEquals(FAST.saltBytes(), decoded.salt().length);
        assertEquals(FAST.hashBytes(), decoded.hash().length);
        assertFalse(PhcFormat.decode(b).salt().length == 0);
    }

    @Test
    @DisplayName("the encoded form is a well-formed PHC argon2id string")
    void encodedShape() {
        String encoded = hasher.hash("x".toCharArray());
        assertTrue(encoded.startsWith("$argon2id$v=19$m=" + FAST.memoryKib() + ",t="
                + FAST.iterations() + ",p=" + FAST.parallelism() + "$"));
        assertTrue(PhcFormat.looksLikeArgon2id(encoded));
    }

    @Test
    @DisplayName("needsRehash is true for weaker parameters and false for the current profile")
    void rehashDetection() {
        assertFalse(hasher.needsRehash(hasher.hash("pw".toCharArray())));

        Argon2idPasswordHasher stronger =
                new Argon2idPasswordHasher(Argon2Parameters.HARDENED, Pepper.none());
        assertTrue(stronger.needsRehash(hasher.hash("pw".toCharArray())));
        // Anything that is not a well-formed argon2id hash must be re-hashed on next login.
        assertTrue(hasher.needsRehash("not-a-hash"));
        assertTrue(hasher.needsRehash(null));
        assertTrue(hasher.needsRehash("$2a$10$legacybcryptvalue"));
    }

    @Test
    @DisplayName("a pepper changes the derived key, so a hash without it cannot be verified")
    void pepperChangesOutput() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        Argon2idPasswordHasher peppered = new Argon2idPasswordHasher(FAST, Pepper.of(key));

        String withPepper = peppered.hash("pw".toCharArray());
        assertTrue(peppered.verify("pw".toCharArray(), withPepper));
        // The unpeppered hasher cannot reproduce the peppered digest.
        assertFalse(hasher.verify("pw".toCharArray(), withPepper));
    }

    @Test
    @DisplayName("verification is defensive against malformed stored values")
    void malformedStoredValue() {
        assertFalse(hasher.verify("pw".toCharArray(), "$argon2id$v=19$m=1,t=1,p=1$bad$bad"));
        assertFalse(hasher.verify("pw".toCharArray(), ""));
        assertFalse(hasher.verify("pw".toCharArray(), null));
    }

    @Test
    @DisplayName("Argon2Parameters rejects unsafe configurations")
    void parameterValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Argon2Parameters(1024, 3, 1, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Parameters(65536, 0, 1, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Parameters(65536, 3, 0, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Parameters(65536, 3, 1, 8, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Parameters(65536, 3, 1, 16, 16));
    }

    @Test
    @DisplayName("clampedToFloor lifts sub-floor parameters back to the OWASP minimum")
    void clamping() {
        Argon2Parameters weak = new Argon2Parameters(8192, 1, 1, 16, 32);
        Argon2Parameters clamped = weak.clampedToFloor();
        assertEquals(Argon2Parameters.FLOOR.memoryKib(), clamped.memoryKib());
        assertEquals(Argon2Parameters.FLOOR.iterations(), clamped.iterations());
        // An already-strong profile is returned unchanged (identity).
        assertEquals(Argon2Parameters.HARDENED, Argon2Parameters.HARDENED.clampedToFloor());
    }

    @Test
    @DisplayName("utf-8 passwords with non-ascii characters round-trip")
    void unicodePasswords() {
        String password = "pässwörd-日本語-🔐";
        String encoded = hasher.hash(password.toCharArray());
        assertTrue(hasher.verify(password.toCharArray(), encoded));
    }

    @Test
    @DisplayName("Pepper.utf8 matches the standard charset encoding")
    void utf8Encoding() {
        char[] chars = "héllo".toCharArray();
        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), Pepper.utf8(chars));
    }
}