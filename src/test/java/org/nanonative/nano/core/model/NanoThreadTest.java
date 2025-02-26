package org.nanonative.nano.core.model;

import org.nanonative.nano.core.config.TestConfig;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.nanonative.nano.core.model.NanoThread.*;
import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class NanoThreadTest {

    public static final ExecutorService TEST_EXECUTOR = VIRTUAL_THREAD_POOL;

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void waitForAll_shouldBlockAndWait() {
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        NanoThread.waitFor(threads);
        assertThat(doneThreads.get()).isEqualTo(TestConfig.TEST_REPEAT);
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void waitForAll_shouldNotBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final Runnable onComplete = latch::countDown;

        NanoThread.waitFor(onComplete, startConcurrentThreads(doneThreads));
        assertThat(latch.await(TestConfig.TEST_REPEAT, TimeUnit.SECONDS)).isTrue();
        assertThat(doneThreads.get()).isEqualTo(TestConfig.TEST_REPEAT);
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void waitFor_shouldBlockAndWait() {
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        Arrays.stream(threads).forEach(NanoThread::await);
        assertThat(doneThreads.get()).isEqualTo(TestConfig.TEST_REPEAT);
        assertThat(Arrays.stream(threads).parallel().allMatch(NanoThread::isComplete)).isTrue();
        assertThat(Arrays.stream(threads).parallel().allMatch(thread -> thread.context() == null)).isTrue();
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void waitFor_shouldNotBlockWait() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(TestConfig.TEST_REPEAT);
        final AtomicInteger doneThreads = new AtomicInteger(0);
        final NanoThread[] threads = startConcurrentThreads(doneThreads);

        Arrays.stream(threads).forEach(thread -> thread.await(latch::countDown));
        assertThat(doneThreads.get()).isLessThan(TestConfig.TEST_REPEAT);
        assertThat(latch.await(TestConfig.TEST_REPEAT, TimeUnit.SECONDS)).isTrue();
        assertThat(doneThreads.get()).isEqualTo(TestConfig.TEST_REPEAT);
        Arrays.stream(threads).parallel().forEach(thread -> assertThat(thread.isComplete()).isTrue());
        Arrays.stream(threads).parallel().forEach(thread -> assertThat(thread.toString()).contains(NanoThread.class.getSimpleName() + "{onCompleteCallbacks=", ", context=false, isComplete=true}"));
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void activeNanoThreadCount() {
        new NanoThread().run(null, null, () -> {
            assertThat(activeNanoThreads()).isPositive();
            assertThat(activeCarrierThreads()).isPositive();
        });
    }

    @SuppressWarnings("java:S2925")
    private static NanoThread[] startConcurrentThreads(final AtomicInteger doneThreads) {
        return IntStream.range(0, TestConfig.TEST_REPEAT).parallel().mapToObj(i -> {
            final NanoThread thread = new NanoThread();
            thread.run(null, null, () -> {
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
