package dev.lightlogin.core.crypto;

import java.util.Objects;

/**
 * A password hashing scheme with a self-describing encoded form.
 *
 * <p>Implementations must be thread-safe: a single instance is shared across every authentication
 * worker. The encoded output carries the algorithm, its parameters and the salt so that a stored
 * hash is always verifiable without out-of-band metadata.</p>
 */
public interface PasswordHasher {

    /**
     * Hashes a password, generating a fresh random salt.
     *
     * @param password the cleartext password; never retained and never logged by implementations
     * @return the self-describing encoded hash, safe to persist
     */
    String hash(char[] password);

    /**
     * Verifies a cleartext password against a stored encoded hash in constant time with respect to
     * the derived key.
     *
     * @return {@code true} only when the password reproduces the stored hash
     */
    boolean verify(char[] password, String encoded);

    /**
     * Reports whether a stored hash was produced with weaker parameters than this hasher currently
     * uses, so the caller can transparently re-hash on a successful login.
     */
    boolean needsRehash(String encoded);

    /** Human-readable algorithm identifier, e.g. {@code argon2id}. */
    default String algorithm() {
        return getClass().getSimpleName();
    }

    /**
     * Convenience for callers holding a {@link String}. The password is converted to a transient
     * {@code char[]} which is wiped immediately afterwards.
     */
    default String hash(CharSequence password) {
        Objects.requireNonNull(password, "password");
        char[] chars = toCharArray(password);
        try {
            return hash(chars);
        } finally {
            ConstantTime.wipe(chars);
        }
    }

    /** @see #hash(CharSequence) */
    default boolean verify(CharSequence password, String encoded) {
        Objects.requireNonNull(password, "password");
        char[] chars = toCharArray(password);
        try {
            return verify(chars, encoded);
        } finally {
            ConstantTime.wipe(chars);
        }
    }

    private static char[] toCharArray(CharSequence sequence) {
        char[] chars = new char[sequence.length()];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = sequence.charAt(i);
        }
        return chars;
    }
}