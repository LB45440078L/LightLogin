package dev.lightlogin.core.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A registry of {@link TokenBucket}s keyed by an arbitrary string (an IP address, a UUID, a
 * username), with periodic eviction of idle entries so the map cannot grow without bound under a
 * distributed attack that cycles through source addresses.
 *
 * <p>Eviction is opportunistic: every {@code sweepInterval} acquisitions, entries whose bucket has
 * been full (unused) for longer than {@code idleTtlNanos} are dropped. This avoids a background
 * timer while still bounding memory.</p>
 */
public final class RateLimiterRegistry {

    private final double capacity;
    private final double refillPerSecond;
    private final long idleTtlNanos;
    private final long sweepInterval;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final AtomicLong acquisitions = new AtomicLong();
    private final LongSupplier nanoClock;

    private static final class Entry {
        final TokenBucket bucket;
        volatile long lastTouchedNanos;

        Entry(TokenBucket bucket, long nowNanos) {
            this.bucket = bucket;
            this.lastTouchedNanos = nowNanos;
        }
    }

    public RateLimiterRegistry(double capacity, double refillPerSecond, long idleTtlNanos) {
        this(capacity, refillPerSecond, idleTtlNanos, 512, System::nanoTime);
    }

    RateLimiterRegistry(double capacity, double refillPerSecond, long idleTtlNanos,
                        long sweepInterval, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.idleTtlNanos = idleTtlNanos;
        this.sweepInterval = sweepInterval;
        this.nanoClock = nanoClock;
    }

    /** Consumes one token for {@code key}, returning whether it was admitted. */
    public boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(new TokenBucket(capacity, refillPerSecond, now), now));
        entry.lastTouchedNanos = now;
        boolean admitted = entry.bucket.tryAcquire();
        if ((acquisitions.incrementAndGet() % sweepInterval) == 0) {
            sweep(now);
        }
        return admitted;
    }

    /** Nanoseconds until {@code key} may act again (0 when admitted now). */
    public long nanosUntilAvailable(String key) {
        Entry entry = buckets.get(key);
        return entry == null ? 0 : entry.bucket.nanosUntilAvailable();
    }

    /** Drops idle entries. */
    public void sweep(long nowNanos) {
        buckets.entrySet().removeIf(e ->
                nowNanos - e.getValue().lastTouchedNanos > idleTtlNanos
                        && e.getValue().bucket.availableTokens() >= capacity);
    }

    /** The number of tracked keys; exposed for tests and metrics. */
    public int size() {
        return buckets.size();
    }

    /** Clears all state (used on reload). */
    public void clear() {
        buckets.clear();
    }
}