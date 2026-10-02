package dev.lightlogin.core.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * Constant-time helpers and best-effort secret wiping.
 *
 * <p>Comparing derived keys with {@link Arrays#equals} leaks the length of the common prefix through
 * timing; every comparison of a secret against attacker-supplied material goes through here.</p>
 */
public final class ConstantTime {

    private ConstantTime() {
    }

    /** Constant-time equality for byte arrays, including a length check. */
    public static boolean equals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        // MessageDigest.isEqual is documented constant-time for equal-length inputs.
        return MessageDigest.isEqual(a, b);
    }

    /** Constant-time equality for strings; UTF-8 encoded before comparison. */
    public static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /** Constant-time equality for char arrays. */
    public static boolean equals(char[] a, char[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /** Overwrites a byte array with zeroes. A no-op for {@code null}. */
    public static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** Overwrites a char array with zero characters. A no-op for {@code null}. */
    public static void wipe(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * Reduces the risk of a password char[] being interned or retained by copying it into a new
     * array that the caller is responsible for wiping.
     */
    public static char[] copy(char[] source) {
        Objects.requireNonNull(source, "source");
        return Arrays.copyOf(source, source.length);
    }
}