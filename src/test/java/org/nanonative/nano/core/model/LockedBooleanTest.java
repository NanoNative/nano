package org.nanonative.nano.core.model;

import org.nanonative.nano.core.config.TestConfig;
import org.nanonative.nano.helper.LockedBoolean;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class LockedBooleanTest {

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void setState_shouldBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        final LockedBoolean lockedBoolean = new LockedBoolean();

        IntStream.range(0, 10).parallel().forEach(i -> NanoThreadTest.TEST_EXECUTOR.submit(() -> {
            lockedBoolean.set(i % 2 == 0);
            latch.countDown();
        }));

        Assertions.assertThat(TestConfig.await(latch)).isTrue();
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void setState_withConsumer_shouldBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        final LockedBoolean lockedBoolean = new LockedBoolean(false);
        final AtomicInteger trueSetCounter = new AtomicInteger(0);
        final AtomicInteger falseSetCounter = new AtomicInteger(0);

        IntStream.range(0, 10).parallel().forEach(i -> NanoThreadTest.TEST_EXECUTOR.submit(() -> {
            if (i % 2 == 0) {
                lockedBoolean.set(true, state -> trueSetCounter.incrementAndGet());
            } else {
                lockedBoolean.set(false, state -> falseSetCounter.incrementAndGet());
            }
            latch.countDown();
        }));

        Assertions.assertThat(TestConfig.await(latch)).isTrue();
        assertThat(trueSetCounter.get() + falseSetCounter.get()).isEqualTo(10);
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void run_withConditionAndConsumer_shouldBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        final LockedBoolean lockedBoolean = new LockedBoolean(false);

        IntStream.range(0, 10).parallel().forEach(i -> NanoThreadTest.TEST_EXECUTOR.submit(() -> lockedBoolean.run(state -> latch.countDown())));

        Assertions.assertThat(TestConfig.await(latch)).isTrue();
        assertThat(lockedBoolean.get()).isFalse();
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void run_withConsumer_shouldBlock() {
        final LockedBoolean lockedBoolean = new LockedBoolean(false);
        final AtomicInteger runCounter = new AtomicInteger(0);

        for (int i = 0; i < 20; i++) {
            final boolean odd = i % 2 == 0;
            lockedBoolean.run(odd, state -> runCounter.incrementAndGet());
        }

        assertThat(lockedBoolean.get()).isFalse();
        assertThat(runCounter).hasValue(10);
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void setState_withCondition_shouldBlock() {
        final LockedBoolean lockedBoolean = new LockedBoolean(true);

        for (int i = 0; i < 10; i++) {
            final boolean odd = i % 2 == 0;
            lockedBoolean.set(odd, !odd);
        }

        assertThat(lockedBoolean.get()).isTrue();
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void setState_withConditionAndConsumer_shouldBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        final LockedBoolean lockedBoolean = new LockedBoolean(true);

        for (int i = 0; i < 10; i++) {
            final boolean odd = i % 2 == 0;
            lockedBoolean.set(odd, !odd, state -> latch.countDown());
        }

        Assertions.assertThat(TestConfig.await(latch)).isTrue();
    }

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void toString_shouldBlock() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        final LockedBoolean lockedBoolean = new LockedBoolean();

        IntStream.range(0, 10).parallel().forEach(i -> NanoThreadTest.TEST_EXECUTOR.submit(() -> {
            assertThat(lockedBoolean.toString()).contains(LockedBoolean.class.getSimpleName() + "{" + "state=false}");
            latch.countDown();
        }));

        Assertions.assertThat(TestConfig.await(latch)).isTrue();
        assertThat(lockedBoolean.get()).isFalse();
    }

}
