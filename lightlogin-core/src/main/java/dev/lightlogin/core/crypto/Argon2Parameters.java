package dev.lightlogin.core.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Arithmetic work factors for an Argon2id hash.
 *
 * <p>The defaults follow the OWASP Password Storage Cheat Sheet recommendation of a
 * memory-hard configuration. Memory is the parameter that buys the most attacker resistance
 * against GPU and ASIC cracking, so it is deliberately the largest knob; raise {@code memoryKib}
 * before {@code iterations} when tuning.</p>
 *
 * @param memoryKib   memory cost in kibibytes (64 MiB by default)
 * @param iterations  time cost, the number of passes over memory
 * @param parallelism degree of parallelism (lanes)
 * @param saltBytes   length of the random salt
 * @param hashBytes   length of the derived key
 */
public record Argon2Parameters(int memoryKib, int iterations, int parallelism, int saltBytes, int hashBytes) {

    /** OWASP minimum-recommended profile: 19 MiB, 2 iterations, 1 lane. */
    public static final Argon2Parameters OWASP_MINIMUM = new Argon2Parameters(19_456, 2, 1, 16, 32);

    /** Balanced default for a game server: 64 MiB, 3 iterations, 1 lane. */
    public static final Argon2Parameters BALANCED = new Argon2Parameters(65_536, 3, 1, 16, 32);

    /** Hardened profile for high-value deployments: 128 MiB, 4 iterations, 1 lane. */
    public static final Argon2Parameters HARDENED = new Argon2Parameters(131_072, 4, 1, 16, 32);

    /** Lower bound accepted from configuration; anything weaker is rejected outright. */
    public static final Argon2Parameters FLOOR = OWASP_MINIMUM;

    public Argon2Parameters {
        if (memoryKib < 8 * 1024) {
            throw new IllegalArgumentException("Argon2 memory cost must be at least 8192 KiB (8 MiB), got " + memoryKib);
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("Argon2 iteration count must be >= 1, got " + iterations);
        }
        if (parallelism < 1 || parallelism > 16) {
            throw new IllegalArgumentException("Argon2 parallelism must be in [1,16], got " + parallelism);
        }
        if (saltBytes < 16) {
            throw new IllegalArgumentException("Salt must be at least 16 bytes, got " + saltBytes);
        }
        if (hashBytes < 32) {
            throw new IllegalArgumentException("Hash output must be at least 32 bytes, got " + hashBytes);
        }
    }

    /**
     * Reports whether {@code other} is weaker than this profile in any dimension. Used to decide
     * whether a stored hash should be upgraded on the next successful login.
     */
    public boolean isStrongerThan(Argon2Parameters other) {
        Objects.requireNonNull(other, "other");
        return memoryKib > other.memoryKib
                || iterations > other.iterations
                || parallelism > other.parallelism
                || hashBytes > other.hashBytes
                || saltBytes > other.saltBytes;
    }

    /** Raises any parameter below the OWASP floor back up to it. */
    public Argon2Parameters clampedToFloor() {
        int m = Math.max(memoryKib, FLOOR.memoryKib);
        int t = Math.max(iterations, FLOOR.iterations);
        int p = Math.max(parallelism, FLOOR.parallelism);
        int s = Math.max(saltBytes, FLOOR.saltBytes);
        int h = Math.max(hashBytes, FLOOR.hashBytes);
        return m == memoryKib && t == iterations && p == parallelism && s == saltBytes && h == hashBytes
                ? this
                : new Argon2Parameters(m, t, p, s, h);
    }

    @Override
    public String toString() {
        return "Argon2Parameters[m=" + memoryKib + "KiB,t=" + iterations + ",p=" + parallelism
                + ",salt=" + saltBytes + ",hash=" + hashBytes + ']';
    }

    /** Exposed for equality helpers in tests without relying on array identity. */
    static boolean sameBytes(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}