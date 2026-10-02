package dev.lightlogin.core.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically secure token generation for sessions, CSRF tokens, access tokens and
 * temporary passwords.
 *
 * <p>Every token is drawn from a {@link SecureRandom} seeded by the operating system CSPRNG, is
 * URL-safe Base64 (no padding) and is compared in constant time. The default entropy is 256 bits,
 * far beyond any feasible brute force.</p>
 */
public final class TokenGenerator {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final SecureRandom random;

    public TokenGenerator() {
        this(new SecureRandom());
    }

    public TokenGenerator(SecureRandom random) {
        this.random = random;
    }

    /** Generates a token carrying {@code byteLength} bytes (256 bits) of entropy. */
    public String token(int byteLength) {
        if (byteLength < 16) {
            throw new IllegalArgumentException("Token entropy must be at least 128 bits (16 bytes)");
        }
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        try {
            return ENCODER.encodeToString(bytes);
        } finally {
            ConstantTime.wipe(bytes);
        }
    }

    /** A 256-bit token. */
    public String token() {
        return token(32);
    }

    /**
     * Generates a human-transcribable temporary password using an alphabet without visually
     * ambiguous characters (no 0/O, 1/l/I). Suitable for emailed recovery passwords.
     */
    public String temporaryPassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Temporary password must be at least 8 characters");
        }
        char[] out = new char[length];
        try {
            for (int i = 0; i < length; i++) {
                out[i] = PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)];
            }
            return new String(out);
        } finally {
            ConstantTime.wipe(out);
        }
    }

    /** A numeric one-time code of {@code digits} digits, for email verification flows. */
    public String numericCode(int digits) {
        if (digits < 4 || digits > 12) {
            throw new IllegalArgumentException("Code length must be in [4,12]");
        }
        StringBuilder builder = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }
}