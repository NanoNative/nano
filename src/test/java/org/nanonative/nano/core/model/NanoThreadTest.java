package org.nanonative.nano.core.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.core.model.NanoThread.activeCarrierThreads;
import static org.nanonative.nano.core.model.NanoThread.activeNanoThreads;

@Execution(ExecutionMode.CONCURRENT)
class NanoThreadTest {

    public static final ExecutorService TEST_EXECUTOR = GLOBAL_THREAD_POOL;

    @RepeatedTest(TEST_REPEAT)
    void waitForAll_shouldBlockAndWait() {
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        NanoThread.waitFor(threads);
        assertThat(doneThreads.get()).isEqualTo(TEST_REPEAT);
    }

    @RepeatedTest(TEST_REPEAT)
    void waitForAll_shouldNotBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final Runnable onComplete = latch::countDown;

        NanoThread.waitFor(onComplete, startConcurrentThreads(doneThreads));
        assertThat(latch.await(TEST_REPEAT, TimeUnit.SECONDS)).isTrue();
        assertThat(doneThreads.get()).isEqualTo(TEST_REPEAT);
    }

    @RepeatedTest(TEST_REPEAT)
    void waitFor_shouldBlockAndWait() {
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        Arrays.stream(threads).forEach(NanoThread::await);
        assertThat(doneThreads.get()).isEqualTo(TEST_REPEAT);
        assertThat(Arrays.stream(threads).parallel().allMatch(NanoThread::isComplete)).isTrue();
    }

    @RepeatedTest(TEST_REPEAT)
    void waitFor_shouldNotBlockWait() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(TEST_REPEAT);
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        Arrays.stream(threads).forEach(thread -> thread.await(latch::countDown));
        assertThat(doneThreads.get()).isLessThan(TEST_REPEAT);
        assertThat(latch.await(TEST_REPEAT, TimeUnit.SECONDS)).isTrue();
        assertThat(doneThreads.get()).isEqualTo(TEST_REPEAT);
        Arrays.stream(threads).parallel().forEach(thread -> assertThat(thread.isComplete()).isTrue());
        Arrays.stream(threads).parallel().forEach(thread -> assertThat(thread.toString()).contains(NanoThread.class.getSimpleName() + "{onCompleteCallbacks=", ", isComplete=true}"));
    }

    @RepeatedTest(TEST_REPEAT)
    void activeNanoThreadCount() {
        new NanoThread().run(() -> null, () -> {
            assertThat(activeNanoThreads()).isPositive();
            assertThat(activeCarrierThreads()).isPositive();
        });
    }

    @SuppressWarnings("java:S2925")
    private static NanoThread[] startConcurrentThreads(final AtomicInteger doneThreads) {
        return IntStream.range(0, TEST_REPEAT).parallel().mapToObj(i -> {
            final NanoThread thread = new NanoThread();
            thread.run(null, () -> {
                try {
                    Thread.sleep((long) (Math.random() * 100));
                    doneThreads.incrementAndGet();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return thread;
        }).toArray(NanoThread[]::new);
    }
}
