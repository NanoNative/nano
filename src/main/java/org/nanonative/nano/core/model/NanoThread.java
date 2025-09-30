package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.LinkedTypeMap;
import org.nanonative.nano.helper.ExRunnable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static org.nanonative.nano.helper.NanoUtils.handleJavaError;

public class NanoThread {

    protected final List<BiConsumer<NanoThread, Throwable>> listeners = new CopyOnWriteArrayList<>();
    protected final AtomicBoolean isComplete = new AtomicBoolean();

    public static final String NANO_THREAD_PREFIX = "nano-thread-";
    public static final ThreadFactory GLOBAL_THREAD_FACTORY = Thread.ofVirtual().name(NANO_THREAD_PREFIX, 0).factory();
    public static final ExecutorService GLOBAL_THREAD_POOL = Executors.newThreadPerTaskExecutor(GLOBAL_THREAD_FACTORY);
    protected static final AtomicLong activeNanoThreadCount = new AtomicLong(0);
    private volatile Future<?> future;

    public boolean isComplete() {
        return isComplete.get();
    }

    public NanoThread onComplete(final BiConsumer<NanoThread, Throwable> listener) {
        // If already done, invoke immediately, but never while holding any lock.
        if (isComplete.get()) {
            listener.accept(this, null);
            return this;
        }
        listeners.add(listener);
        // Edge race: if completion happened between get() and add(), check again.
        if (isComplete.get()) {
            // Best effort to notify late subscribers promptly.
            listener.accept(this, null);
        }
        return this;
    }

    public NanoThread await() {
        return await(null);
    }

    public NanoThread await(final Runnable onDone) {
        return waitFor(onDone, this)[0];
    }

    @SuppressWarnings("java:S1181") // Throwable is caught
    public NanoThread run(final Supplier<Context> context, final ExRunnable task) {
        future = GLOBAL_THREAD_POOL.submit(() -> {
            try {
                activeNanoThreadCount.incrementAndGet();
                task.run();
                markComplete(null);
            } catch (final Throwable error) {
                handleJavaError(context, error);
                markComplete(error);
                ofNullable(context).map(Supplier::get)
                    .ifPresent(ctx -> ctx.sendEventError(task, error));
            } finally {
                activeNanoThreadCount.decrementAndGet();
            }
        });
        return this;
    }

    private void markComplete(final Throwable error) {
        if (isComplete.compareAndSet(false, true)) {
            // Invoke user callbacks outside any lock, once.
            for (final BiConsumer<NanoThread, Throwable> l : listeners) {
                try {
                    l.accept(this, error);
                } catch (final Exception ignored) {
                    // ignored
                }
            }
            listeners.clear();
        }
    }

    public Future<?> future() {
        return future;
    }

    public NanoThread future(final Future<?> future) {
        this.future = future;
        return this;
    }

    public static long activeNanoThreads() {
        return activeNanoThreadCount.get();
    }

    public static long activeCarrierThreads() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final long[] threadIds = threadMXBean.getAllThreadIds();
        return Arrays.stream(threadMXBean.getThreadInfo(threadIds))
            .filter(Objects::nonNull)
            .filter(info -> (info.getThreadName() != null && info.getThreadName().startsWith("CarrierThread"))
                || (info.getLockName() != null && info.getLockName().startsWith("java.lang.VirtualThread"))
                || (info.getLockName() != null && info.getLockName().startsWith(NANO_THREAD_PREFIX))
                || (info.getLockOwnerName() != null && info.getLockName().startsWith(NANO_THREAD_PREFIX))
            )
            .count();
    }

    /**
     * Blocks until all provided {@code NanoThread} instances have completed execution.
     * This method waits indefinitely for all threads to finish.
     *
     * @param threads An array of {@code NanoThread} instances to wait for.
     * @return The same array of {@code NanoThread} instances, allowing for method chaining or further processing.
     */
    public static NanoThread[] waitFor(final NanoThread... threads) {
        return waitFor(null, threads);
    }

    /**
     * Waits for all provided {@link NanoThread} instances to complete execution and optionally executes
     * a {@link Runnable} once all threads have finished. If {@code onComplete} is not null, it will be
     * executed asynchronously after all threads have completed. This variant allows for non-blocking
     * behavior if {@code onComplete} is provided, where the method returns immediately, and the
     * {@code onComplete} action is executed in the background once all threads are done.
     *
     * @param onComplete An optional {@link Runnable} to execute once all threads have completed.
     *                   If null, the method blocks until all threads are done. If non-null, the method
     *                   returns immediately, and the {@code Runnable} is executed asynchronously
     *                   after thread completion.
     * @param threads    An array of {@link NanoThread} instances to wait for.
     * @return The same array of {@link NanoThread} instances, allowing for method chaining or further processing.
     */
    public static NanoThread[] waitFor(final Runnable onComplete, final NanoThread... threads) {
        final CountDownLatch latch = new CountDownLatch(threads.length);
        for (final NanoThread thread : threads) {
            thread.onComplete((nt, error) -> {
                latch.countDown();
                if (!(error instanceof Error) && latch.getCount() == 0 && onComplete != null) onComplete.run();
            });
        }
        if (onComplete == null) {
            // Wait up to configurable timeout; if it expires, dump diagnostics then block a bit longer
            try {
                if (!latch.await(10000L, TimeUnit.MILLISECONDS)) {
                    System.err.println(new Date() + " [FATAL] Threads did not complete in " + 10000L + "ms");
                    for (final NanoThread t : threads) {
                        if (t.future() != null) t.future().cancel(true); // cooperative cancel
                    }
                }
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return threads;
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("class", this.getClass().getSimpleName())
            .putR("listener", listeners.size())
            .putR("isComplete", isComplete.get())
            .toJson();
    }
}
