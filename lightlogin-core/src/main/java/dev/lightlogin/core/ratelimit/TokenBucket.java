package dev.lightlogin.core.ratelimit;

/**
 * A classic token bucket: {@code capacity} tokens, refilled continuously at
 * {@code refillPerSecond}. Each admitted request consumes one token.
 *
 * <p>Token buckets model a sustained rate with a bounded burst, which is exactly the shape of
 * legitimate traffic (a player retrying a mistyped password) versus an attack (thousands of
 * attempts per second). Refill is computed from elapsed wall time rather than a scheduled task, so
 * an idle bucket costs nothing and there is no timer to leak.</p>
 *
 * <p>Thread-safe.</p>
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;
    private final long nanosPerToken;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(double capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System.nanoTime());
    }

    TokenBucket(double capacity, double refillPerSecond, long nowNanos) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be > 0");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.nanosPerToken = (long) (1_000_000_000.0 / refillPerSecond);
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** Attempts to consume one token, returning whether the request is admitted. */
    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    /** Attempts to consume {@code count} tokens. */
    public synchronized boolean tryAcquire(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        refill(System.nanoTime());
        if (tokens >= count) {
            tokens -= count;
            return true;
        }
        return false;
    }

    /** Nanoseconds until one token is available, or 0 when one is available now. */
    public synchronized long nanosUntilAvailable() {
        refill(System.nanoTime());
        if (tokens >= 1) {
            return 0;
        }
        return (long) ((1 - tokens) * nanosPerToken);
    }

    /** The currently available token count, after refill. */
    public synchronized double availableTokens() {
        refill(System.nanoTime());
        return tokens;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double gained = (elapsed / 1_000_000_000.0) * refillPerSecond;
        if (gained > 0) {
            tokens = Math.min(capacity, tokens + gained);
            lastRefillNanos = nowNanos;
        }
    }
}