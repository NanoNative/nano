package org.nanonative.nano.core.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.core.model.NanoThread.activeCarrierThreads;
import static org.nanonative.nano.core.model.NanoThread.activeNanoThreads;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;

@Execution(ExecutionMode.CONCURRENT)
class NanoThreadTest {

    // We deliberately track completions via AtomicInteger instead of a CountDownLatch.
    // The latch inside waitFor(onComplete, ...) only tells us when the non-blocking callback fired.
    // The AtomicInteger increments inside the worker body, proving that the work truly ran.
    // Virtual threads can reschedule between the worker increment and the onComplete callback,
    // so assertions must tolerate that brief visibility race (hence the waitForCondition below).

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
        // waitFor(onComplete, ...) returns immediately, so the callback can fire before the AtomicInteger increment
        // becomes visible. We poll until all increments are observed instead of blocking on another latch,
        // which would defeat the non-blocking contract we want to verify here.
        assertThat(waitForCondition(() -> doneThreads.get() == TEST_REPEAT, TEST_TIMEOUT)).isTrue();
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
        Arrays.stream(threads).parallel().forEach(thread -> assertThat(thread.toString()).contains("{\"class\":\"NanoThread\",\"listener\":"));
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
