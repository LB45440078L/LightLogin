package dev.lightlogin.core.concurrent;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The asynchronous execution context for every blocking operation in the plugin.
 *
 * <p>Two design choices matter. First, tasks run on <em>virtual threads</em> (stable since Java 21):
 * they are cheap enough to create one per login, and blocking on a JDBC socket or an SMTP round
 * trip parks the virtual thread rather than a platform carrier thread, so the server's main thread
 * and its scheduler are never touched by authentication work.</p>
 *
 * <p>Second, a {@link Semaphore} bounds concurrency. This is not about thread cost — virtual
 * threads are nearly free — but about <em>resource</em> cost: an Argon2id hash at the 64 MiB profile
 * allocates 64 MiB for its duration, and an unbounded flood of logins would otherwise let a burst
 * exhaust the heap. The permit is the backpressure that turns "the server OOMs" into "the excess
 * logins queue for a few hundred milliseconds".</p>
 */
public final class AsyncExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Semaphore permits;
    private final int maxConcurrency;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private volatile boolean closing;

    public AsyncExecutor(int maxConcurrency, String threadNamePrefix) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.maxConcurrency = maxConcurrency;
        this.permits = new Semaphore(maxConcurrency, true);
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(threadNamePrefix + "-", 0).factory());
    }

    /**
     * Runs {@code task} asynchronously, respecting the concurrency bound.
     *
     * @return a future completing with the task's result
     * @throws RejectedExecutionException when the executor is shutting down
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        if (closing) {
            throw new java.util.concurrent.RejectedExecutionException("AsyncExecutor is closing");
        }
        submitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException("interrupted while awaiting a permit");
            }
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                permits.release();
            }
        }, executor);
    }

    /** Runs a side-effecting task asynchronously. */
    public CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /** The configured concurrency ceiling. */
    public int maxConcurrency() {
        return maxConcurrency;
    }

    /** Permits currently free; a proxy for outstanding authentication work. */
    public int availablePermits() {
        return permits.availablePermits();
    }

    /** Tasks accepted since construction. */
    public long submittedCount() {
        return submitted.get();
    }

    /** Tasks refused because the executor was closing. */
    public long rejectedCount() {
        return rejected.get();
    }

    @Override
    public void close() {
        closing = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}