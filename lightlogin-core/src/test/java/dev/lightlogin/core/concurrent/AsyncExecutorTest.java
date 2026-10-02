package dev.lightlogin.core.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The async executor is what keeps authentication work off the server's tick thread, so its two
 * contracts are worth pinning down: work really runs on a different thread, and the semaphore
 * really bounds concurrency (the backpressure that stops a login burst exhausting the heap).
 */
class AsyncExecutorTest {

    @Test
    @DisplayName("work runs on a different thread from the caller")
    @Timeout(30)
    void runsOffTheCallingThread() throws Exception {
        try (AsyncExecutor executor = new AsyncExecutor(2, "test")) {
            Thread caller = Thread.currentThread();
            AtomicReference<Thread> worker = new AtomicReference<>();

            executor.submit(() -> {
                worker.set(Thread.currentThread());
                return null;
            }).get(10, TimeUnit.SECONDS);

            assertTrue(worker.get() != null);
            assertNotEquals(caller, worker.get(), "work must not run on the calling thread");
        }
    }

    @Test
    @DisplayName("the concurrency ceiling is respected even when many tasks are submitted at once")
    @Timeout(60)
    void boundsConcurrency() throws Exception {
        int ceiling = 3;
        int tasks = 24;
        try (AsyncExecutor executor = new AsyncExecutor(ceiling, "test")) {
            AtomicInteger active = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            CountDownLatch release = new CountDownLatch(1);
            // Only `ceiling` tasks can be holding a permit at once, so only that many can reach the
            // body. Waiting for all `tasks` to start would contradict the contract being tested.
            CountDownLatch running = new CountDownLatch(ceiling);
            CountDownLatch finished = new CountDownLatch(tasks);

            for (int i = 0; i < tasks; i++) {
                executor.run(() -> {
                    int now = active.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    running.countDown();
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        active.decrementAndGet();
                        finished.countDown();
                    }
                });
            }

            // Submitting must not block: all 24 tasks are dispatched, but only `ceiling` of them
            // may actually run while the gate is held and the rest must queue on the semaphore.
            assertTrue(running.await(20, TimeUnit.SECONDS),
                    "the ceiling number of tasks should have started");
            Thread.sleep(200);
            assertTrue(active.get() <= ceiling,
                    "active concurrency " + active.get() + " exceeded the ceiling of " + ceiling);
            assertEquals(tasks, finished.getCount(),
                    "no task may complete while every permit is held, so the excess must be queued");

            release.countDown();
            assertTrue(finished.await(30, TimeUnit.SECONDS),
                    "every queued task should run once permits are released");
            assertTrue(peak.get() <= ceiling,
                    "peak concurrency " + peak.get() + " exceeded the ceiling of " + ceiling);
            assertEquals(ceiling, executor.availablePermits(),
                    "every permit must be returned once the work drains");
        }
    }

    @Test
    @DisplayName("each task gets its own virtual thread")
    @Timeout(60)
    void usesVirtualThreads() throws Exception {
        try (AsyncExecutor executor = new AsyncExecutor(8, "test")) {
            Set<String> names = ConcurrentHashMap.newKeySet();
            Set<Boolean> virtual = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(16);

            for (int i = 0; i < 16; i++) {
                executor.run(() -> {
                    names.add(Thread.currentThread().getName());
                    virtual.add(Thread.currentThread().isVirtual());
                    latch.countDown();
                });
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS));

            assertTrue(virtual.contains(true), "tasks must run on virtual threads");
            assertTrue(names.size() > 1, "tasks should not share a thread");
        }
    }

    @Test
    @DisplayName("a task failure does not poison the executor")
    @Timeout(30)
    void failureDoesNotStopTheExecutor() throws Exception {
        try (AsyncExecutor executor = new AsyncExecutor(2, "test")) {
            executor.run(() -> {
                throw new IllegalStateException("boom");
            });
            // A later task still runs and still returns a value.
            Integer result = executor.submit(() -> 42).get(10, TimeUnit.SECONDS);
            assertEquals(42, result);
        }
    }
}