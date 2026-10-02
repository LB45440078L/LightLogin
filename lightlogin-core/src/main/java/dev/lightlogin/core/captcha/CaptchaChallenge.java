package dev.lightlogin.core.captcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * A single anti-bot challenge issued to a joining player.
 *
 * <p>Two strategies are provided. The arithmetic challenge is the human-friendly default that
 * mirrors the original plugin's maths question. The proof-of-work challenge is the answer to
 * automated flooding: it costs a script a measurable amount of CPU per attempt while remaining
 * invisible to a human, so the cost of a bot flood rises from "one packet" to "one hash search per
 * attempt" without any third-party CAPTCHA service.</p>
 */
public sealed interface CaptchaChallenge permits CaptchaChallenge.Arithmetic, CaptchaChallenge.ProofOfWork {

    /** An opaque identifier tying the challenge to a subject (usually a player UUID). */
    String id();

    /** The human-readable prompt shown to the player. */
    String prompt();

    /** The instant (epoch millis) after which the challenge is refused. */
    long expiresAtMillis();

    /** Whether the challenge has expired relative to {@code nowMillis}. */
    default boolean isExpired(long nowMillis) {
        return nowMillis > expiresAtMillis();
    }

    /** Validates an answer. Implementations must not throw for arbitrary input. */
    boolean validate(String answer);

    /** A simple arithmetic challenge, generated from a CSPRNG. */
    record Arithmetic(String id, int left, int right, char operator, int result, long expiresAtMillis)
            implements CaptchaChallenge {

        public Arithmetic {
            Objects.requireNonNull(id, "id");
        }

        /**
         * Generates an arithmetic challenge. Operators are limited to those whose result a player
         * can produce quickly in their head.
         */
        public static Arithmetic generate(String id, java.util.random.RandomGenerator rng, long ttlMillis, long nowMillis) {
            int left = 2 + rng.nextInt(48);
            int right = 2 + rng.nextInt(24);
            char operator = switch (rng.nextInt(3)) {
                case 0 -> '+';
                case 1 -> '-';
                default -> 'x';
            };
            // Keep subtraction non-negative and multiplication small.
            if (operator == '-' && right > left) {
                int swap = left;
                left = right;
                right = swap;
            }
            if (operator == 'x') {
                left = 2 + rng.nextInt(9);
                right = 2 + rng.nextInt(9);
            }
            int result = switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                default -> left * right;
            };
            return new Arithmetic(id, left, right, operator, result, nowMillis + ttlMillis);
        }

        @Override
        public String prompt() {
            return left + " " + operator + " " + right;
        }

        @Override
        public boolean validate(String answer) {
            if (answer == null) {
                return false;
            }
            try {
                return Integer.parseInt(answer.trim()) == result;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    /**
     * A hashcash-style proof-of-work challenge: find a nonce {@code n} such that
     * {@code sha256(seed + ':' + n)} has at least {@code difficultyBits} leading zero bits.
     */
    record ProofOfWork(String id, String seed, int difficultyBits, long expiresAtMillis)
            implements CaptchaChallenge {

        public ProofOfWork {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(seed, "seed");
            if (difficultyBits < 8 || difficultyBits > 28) {
                throw new IllegalArgumentException("difficultyBits must be in [8,28]");
            }
        }

        @Override
        public String prompt() {
            return "Solve the proof-of-work (difficulty " + difficultyBits + " bits): " + seed;
        }

        @Override
        public boolean validate(String answer) {
            if (answer == null || answer.isBlank()) {
                return false;
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(seed.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                byte[] hash = digest.digest(answer.trim().getBytes(StandardCharsets.UTF_8));
                return leadingZeroBits(hash) >= difficultyBits;
            } catch (java.security.NoSuchAlgorithmException e) {
                return false;
            }
        }

        /** Counts leading zero bits in a digest. */
        public static int leadingZeroBits(byte[] hash) {
            int bits = 0;
            for (byte b : hash) {
                if (b == 0) {
                    bits += 8;
                    continue;
                }
                bits += Integer.numberOfLeadingZeros(b & 0xFF) - 24;
                break;
            }
            return bits;
        }
    }
}