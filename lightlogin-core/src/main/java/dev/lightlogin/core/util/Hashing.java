package dev.lightlogin.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Small hashing helpers used for values that are compared but never need to be reversible. */
public final class Hashing {

    private Hashing() {
    }

    /**
     * SHA-256 of a string, hex-encoded. Used to store session tokens as digests so a database leak
     * cannot be replayed; the entropy already in the token makes a plain digest sufficient (there
     * is no low-entropy secret to brute-force here, unlike a password).
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}