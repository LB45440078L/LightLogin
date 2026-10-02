package dev.lightlogin.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * A server-side secret mixed into every password before it reaches Argon2.
 *
 * <p>A pepper is defence-in-depth for the case where the database leaks but the filesystem does
 * not: the attacker cannot even begin offline cracking without it. It is applied as an
 * HMAC-SHA-512 keyed by the pepper, which both hides the password from the memory-hard function's
 * input and avoids length-extension concerns.</p>
 */
public sealed interface Pepper permits Pepper.None, Pepper.Keyed {

    /** Applies the pepper to UTF-8 password bytes, returning the material to hash. */
    byte[] apply(byte[] passwordBytes);

    /** Whether this pepper actually changes the hash input. */
    boolean isEnabled();

    /** No pepper configured; the password is hashed as-is. */
    record None() implements Pepper {
        @Override
        public byte[] apply(byte[] passwordBytes) {
            return passwordBytes;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    /** HMAC-SHA-512 pepper keyed by {@code key}. */
    record Keyed(byte[] key) implements Pepper {

        public Keyed {
            if (key == null || key.length < 16) {
                throw new IllegalArgumentException("Pepper key must be at least 16 bytes");
            }
            key = Arrays.copyOf(key, key.length);
        }

        @Override
        public byte[] apply(byte[] passwordBytes) {
            try {
                Mac mac = Mac.getInstance("HmacSHA512");
                mac.init(new SecretKeySpec(key, "HmacSHA512"));
                return mac.doFinal(passwordBytes);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HmacSHA512 unavailable", e);
            }
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    /** The disabled pepper. */
    Pepper NONE = new None();

    static Pepper none() {
        return NONE;
    }

    static Pepper of(byte[] key) {
        return new Keyed(key);
    }

    /** Generates a fresh random pepper of {@code bytes} length. */
    static Pepper generate(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return new Keyed(key);
    }

    /** Converts a char[] password to UTF-8 bytes without retaining a String. */
    static byte[] utf8(char[] password) {
        java.nio.ByteBuffer buffer = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}