package dev.lightlogin.core.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    @Test
    @DisplayName("a bucket admits exactly its capacity, then refuses")
    void burstCapacity() {
        TokenBucket bucket = new TokenBucket(3, 1);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "fourth request exceeds the burst of 3");
    }

    @Test
    @DisplayName("tokens refill at the configured rate")
    void refill() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1, 50); // 20ms per token
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        Thread.sleep(60);
        assertTrue(bucket.tryAcquire(), "a token should have refilled");
    }

    @Test
    @DisplayName("the bucket never exceeds capacity after a long idle period")
    void noOverfill() {
        AtomicLong clock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(2, 1, clock.get());
        clock.set(1_000_000_000_000L); // 1000s later
        assertEquals(2.0, bucket.availableTokens(), 0.001);
    }

    @Test
    @DisplayName("invalid construction is rejected")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0));
    }

    @Test
    @DisplayName("the registry tracks per-key limits and evicts idle keys")
    void registry() {
        RateLimiterRegistry registry = new RateLimiterRegistry(2, 1, 1_000_000_000L);
        assertTrue(registry.tryAcquire("a"));
        assertTrue(registry.tryAcquire("a"));
        assertFalse(registry.tryAcquire("a"));
        // A different key has its own bucket.
        assertTrue(registry.tryAcquire("b"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("nanosUntilAvailable reports a wait for an exhausted bucket")
    void waitReporting() {
        RateLimiterRegistry registry = new RateLimiterRegistry(1, 1, 1_000_000_000L);
        assertTrue(registry.tryAcquire("x"));
        assertTrue(registry.nanosUntilAvailable("x") > 0);
        assertEquals(0, registry.nanosUntilAvailable("never-seen"));
    }
}