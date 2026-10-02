package dev.lightlogin.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;

/**
 * Time-based one-time passwords (RFC 6238), for optional two-factor authentication on the admin
 * panel.
 *
 * <p>Implemented directly on the JDK's HMAC so the plugin does not need to shade a TOTP library.
 * Verification accepts the current 30-second step and its immediate neighbours, which tolerates
 * clock skew without widening the window enough to matter.</p>
 */
public final class Totp {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;

    private Totp() {
    }

    /** Generates the 6-digit code for a base32 secret at a given time. */
    public static String code(String base32Secret, long epochMillis) {
        byte[] key = decodeBase32(base32Secret);
        long counter = (epochMillis / 1000) / STEP_SECONDS;
        byte[] hash = hmacSha1(key, ByteBuffer.allocate(8).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
    }

    /** Verifies a user-supplied code against the current time, within the skew window. */
    public static boolean verify(String base32Secret, String code, long epochMillis) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String normalised = code.replace(" ", "");
        for (int drift = -WINDOW; drift <= WINDOW; drift++) {
            long t = epochMillis + drift * STEP_SECONDS * 1000L;
            if (dev.lightlogin.core.crypto.ConstantTime.equals(code(base32Secret, t), normalised)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a string is a plausible base32 TOTP secret. */
    public static boolean isWellFormed(String base32Secret) {
        if (base32Secret == null || base32Secret.isBlank()) {
            return false;
        }
        String upper = base32Secret.toUpperCase(Locale.ROOT).replace("=", "");
        if (upper.length() < 16) {
            return false;
        }
        for (int i = 0; i < upper.length(); i++) {
            if (BASE32_ALPHABET.indexOf(upper.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    private static byte[] decodeBase32(String secret) {
        String normalised = secret.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "");
        int bits = 0;
        int value = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < normalised.length(); i++) {
            int index = BASE32_ALPHABET.indexOf(normalised.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + normalised.charAt(i));
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}