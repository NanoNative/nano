package org.nanonative.nano.core;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.config.TestConfig;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.model.TestService;
import org.nanonative.nano.services.logging.model.LogLevel;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.Context.APP_HELP;
import static org.nanonative.nano.core.model.Context.APP_PARAMS;
import static org.nanonative.nano.core.model.Context.CONFIG_PARALLEL_SHUTDOWN;
import static org.nanonative.nano.core.model.Context.CONFIG_PROFILES;
import static org.nanonative.nano.core.model.Context.CONTEXT_CLASS_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_NANO_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_PARENT_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_TRACE_ID_KEY;
import static org.nanonative.nano.core.model.Context.EVENT_APP_ERROR;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SHUTDOWN;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;
import static org.nanonative.nano.model.TestService.TEST_EVENT;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;
import static org.nanonative.nano.services.logging.model.LogLevel.INFO;

@Execution(ExecutionMode.CONCURRENT)
class NanoTest {

    @Test
    void configFilesTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, "app_profile", "local", "app_profiles", "default, local, dev, prod"));
        assertThat(nano.context().asString(CONFIG_PROFILES)).isEqualTo("default, local, dev, prod");
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).contains("local", "default", "dev", "prod");
        assertThat(nano.context().asString("test_placeholder_fallback")).isEqualTo("fallback should be used 1");
        assertThat(nano.context().asString("test_placeholder_key_empty")).isEqualTo("fallback should be used 2");
        assertThat(nano.context().asString("test_placeholder_value")).isEqualTo("used placeholder value");
        assertThat(nano.context().asString("resource_key1")).isEqualTo("AA");
        assertThat(nano.context().asString("resource_key2")).isEqualTo("CC");
        assertThat(nano.context()).doesNotContainKey("test_placeholder_fallback_empty");
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @Test
    void testCustomProfile() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, LogLevel.ALL, "app_profiles", "custom"));
        assertThat(nano.context().asString("resource_key1")).isEqualTo("AA");
        assertThat(nano.context().asString("resource_key2")).isEqualTo("ZZ");
        assertThat(nano.shutdown(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void stopViaMethod() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano.stop(this.getClass()).waitForStop()).isNotNull().isEqualTo(nano);
    }

    @RepeatedTest(TEST_REPEAT)
    void stopViaEvent() {
        final Context actual = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL)).context(this.getClass()).newEvent(EVENT_APP_SHUTDOWN).sendR();
        assertThat(actual).isNotNull();
        assertThat(actual.nano().stop(this.getClass()).waitForStop()).isNotNull().isEqualTo(actual.nano());
    }

    @RepeatedTest(TEST_REPEAT)
    void startMultipleTimes_shouldHaveNoIssues() {
        final TestService service1 = new TestService();
        final TestService service2 = new TestService();
        final Nano nano1 = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service1);
        final Nano nano2 = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service2);
        assertThat(nano1).isNotEqualTo(nano2);
        stopAndTestNano(nano1, service1);
        stopAndTestNano(nano2, service2);
    }

    @RepeatedTest(TEST_REPEAT)
    void shutdownServicesInParallelTest_Sync() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(8);

        final Nano nano1 = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown))
        );
        final Nano nano2 = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, CONFIG_PARALLEL_SHUTDOWN, true),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown)),
                new TestService().doOnStop(context -> context.tryExecute(latch::countDown))
        );
        assertThat(nano1.stop(this.getClass())).isEqualTo(nano1);
        assertThat(nano2.stop(this.getClass())).isEqualTo(nano2);
        assertThat(nano1.waitForStop().isReady()).isFalse();
        assertThat(nano2.waitForStop().isReady()).isFalse();
        nano1.shutdown(this.getClass());
        Assertions.assertThat(TestConfig.await(latch)).isTrue();
        assertThat(nano1.waitForStop()).isNotNull().isEqualTo(nano1);
        assertThat(nano2.waitForStop()).isNotNull().isEqualTo(nano2);
    }

    @RepeatedTest(TEST_REPEAT)
    void shutdownServicesInParallelWithExceptionTest() {
        final TestService testService = new TestService();
        testService.doOnStop(context -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, CONFIG_PARALLEL_SHUTDOWN, true), testService).shutdown(this.getClass());
        assertThat(nano).isNotNull();
        assertThat(nano.stop(this.getClass()).waitForStop()).isNotNull().isEqualTo(nano);
    }

    @Disabled("No args constructor test is changing the log level of the test. Since the java logger is not stateless, it affects the other tests.")
    @RepeatedTest(TEST_REPEAT)
    void constructorNoArgsTest() {
        final Nano nano = new Nano();
        assertThat(nano).isNotNull();
        nano.context.newEvent(EVENT_CONFIG_CHANGE, () -> Map.of(CONFIG_LOG_LEVEL, INFO)).send();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(INFO);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withConfigTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        nano.context.newEvent(EVENT_CONFIG_CHANGE, () -> Map.of(CONFIG_LOG_LEVEL, INFO)).broadcast(true).send();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(INFO);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withConfigAndServiceTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new TestService());
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withConfigAndLazyServices_Test() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), context -> List.of(new TestService()));
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withArgsAndLazyServices_Test() {
        final Nano nano = new Nano(new String[]{"-" + CONFIG_LOG_LEVEL + "=" + TEST_LOG_LEVEL}, context -> List.of(new TestService()));
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withLazyServices_Test() {
        final Nano nano = new Nano(context -> List.of(new TestService()), null, "-" + CONFIG_LOG_LEVEL + "=" + TEST_LOG_LEVEL);
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void printParameterTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, APP_PARAMS, true, APP_HELP, true));
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void toStringTest() {
        final Nano config = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, APP_PARAMS, true));
        assertThat(config).isNotNull();
        assertThat(config.toString()).contains(
                "pid=",
                "schedulers=", "services=", "listeners=",
                "cores=", "usedMemory=",
                "threadsNano=", "threadsActive=", "threadsOther=",
                "java=", "arch=", "os="
        );
        assertThat(config.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void sendEvent_Sync() throws InterruptedException {
        final TestService service = new TestService().doOnEvent(event -> event.channel(TEST_EVENT).ifPresent(e -> e.respond(e.payload())));
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service);

        // send to first service
        final CountDownLatch latch1 = new CountDownLatch(1);
        nano.context(this.getClass()).newEvent(TEST_EVENT, () -> 11111111).async(response -> latch1.countDown()).send();
        assertThat(service.getEvent(TEST_EVENT, event -> event.payloadOpt().filter(Integer.class::isInstance).map(Integer.class::cast).map(i -> i.equals(11111111)).isPresent())).isNotNull();
        assertThat(latch1.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();

        // send to first listener (listeners have priority)
        service.resetEvents();
        final CountDownLatch latch2 = new CountDownLatch(1);
        nano.subscribeEvent(TEST_EVENT, e -> e.acknowledge());
        nano.context(this.getClass()).newEvent(TEST_EVENT, () -> 22222222).async(response -> latch2.countDown()).send();
        assertThat(latch2.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThatThrownBy(() -> service.getEvent(TEST_EVENT, 64)).isInstanceOf(AssertionError.class);

        // send to all (listener and services)
        service.resetEvents();
        final CountDownLatch latch3 = new CountDownLatch(1);
        nano.context(this.getClass()).newEvent(TEST_EVENT, () -> 33333333).async(response -> latch3.countDown()).broadcast(true).send();
        assertThat(latch3.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(service.getEvent(TEST_EVENT, event -> ((Integer) 33333333).equals(event.payloadAck()))).isNotNull();

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void sendEventWithEventExecutionException_shouldNotInterrupt() {
        final TestService service = new TestService();
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, LogLevel.OFF), service);

        service.doOnEvent(event -> {
            if (event.channel(TEST_EVENT).isPresent())
                throw new RuntimeException("Nothing to see here, just a test exception");
        });

        final Context context = nano.contextEmpty(this.getClass());
        assertThat(context).hasSize(4).containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY, CONTEXT_PARENT_KEY);

        context.newEvent(TEST_EVENT, () -> 44444444).async(true).send();
        assertThat(service.getEvent(EVENT_APP_ERROR, event -> event.channel(TEST_EVENT).get().payload().equals(44444444))).isNotNull();
        assertThat(service.startCount()).isEqualTo(1);
        assertThat(service.stopCount()).isZero();
        assertThat(service.failures()).isNotEmpty();
        assertThat(nano.isReady()).isTrue();

        nano.shutdown(context);
        assertThat(service.stopCount()).isEqualTo(1);
        assertThat(nano.waitForStop()).isNotNull().isEqualTo(nano);
    }

    @RepeatedTest(TEST_REPEAT)
    void addAndRemoveEventListener() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        final Consumer<Event<Object, Object>> listener = event -> {};

        assertThat(nano.listeners().get(TEST_EVENT.id())).isNull();
        nano.subscribeEvent(TEST_EVENT, listener);
        assertThat(nano.listeners().get(TEST_EVENT.id())).hasSize(1);
        nano.unsubscribeEvent(TEST_EVENT.id(), listener);
        assertThat(nano.listeners().get(TEST_EVENT.id())).isEmpty();

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(32)
        // custom repeats as they are time heavy tests
    void runSchedulers() throws InterruptedException {
        final long timer = 64;
        final CountDownLatch scheduler1Triggered = new CountDownLatch(1);
        final CountDownLatch scheduler2Triggered = new CountDownLatch(1);
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));

        nano.run(null, scheduler1Triggered::countDown, timer, MILLISECONDS);
        nano.run(null, scheduler2Triggered::countDown, timer, timer * 2, MILLISECONDS, () -> false);

        assertThat(scheduler1Triggered.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(scheduler2Triggered.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(32) // still time-heavy, but quick per run
    void schedulerRunDayOfWeek() throws InterruptedException {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        try {
            final DayOfWeek today = LocalDateTime.now().getDayOfWeek();

            // 1) Past time today => should NOT run now (next occurrence is next week)
            final CountDownLatch shouldNotFire = new CountDownLatch(1);
            nano.run(
                    nano::context,
                    shouldNotFire::countDown,
                    LocalTime.now().minusHours(1), // past
                    today,
                    () -> false
            );
            // Give a short window; it must NOT fire
            assertThat(shouldNotFire.await(120, MILLISECONDS)).isFalse();

            // 2) Near-future time today => should run once, soon
            final CountDownLatch shouldFire = new CountDownLatch(1);
            nano.run(
                    nano::context,
                    shouldFire::countDown,
                    LocalTime.now().plusNanos(50_000_000), // ~50ms
                    today,
                    () -> false
            );
            assertThat(shouldFire.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        } finally {
            nano.stop(this.getClass()).waitForStop();
        }
    }

    @RepeatedTest(TEST_REPEAT)
    void throwExceptionInsideScheduler() throws InterruptedException {
        final long timer = 64;
        final TestService service = new TestService();
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service);
        final CountDownLatch trigger = new CountDownLatch(2);

        nano.run(nano::context, () -> {
            trigger.countDown();
            throw new RuntimeException("Nothing to see here, just a test exception");
        }, timer, MILLISECONDS);

        nano.run(nano::context, () -> {
            trigger.countDown();
            throw new RuntimeException("Nothing to see here, just a test exception");
        }, timer, timer * 2, MILLISECONDS, () -> false);

        assertThat(trigger.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(service.getEvent(EVENT_APP_ERROR, event -> event.payload() != null)).isNotNull();
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void errorHandlerTest() {
        final TestService service = new TestService().doOnEvent(event -> event.channel(TEST_EVENT).ifPresent(e -> e.respond(true)));
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service);

        // Execution with error
        nano.context(NanoTest.class).run(() -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });

        assertThat(service.getEvent(EVENT_APP_ERROR)).isNotNull();
        service.resetEvents();

        // Event with error
        nano.subscribeEvent(TEST_EVENT, event -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });

        // Trigger error
        nano.context(NanoTest.class).newEvent(TEST_EVENT, () -> "test").send();

        assertThat(service.getEvent(EVENT_APP_ERROR)).isNotNull();
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    private static void stopAndTestNano(final Nano nano, final TestService service) {
        assertThat(nano.isReady()).isTrue();
        assertThat(nano.createdAtMs()).isPositive();
        assertThat(nano.pid()).isPositive();
        assertThat(nano.usedMemoryMB()).isPositive();
        //assertThat(nano.usedMemoryMB()).isLessThan(TEST_REPEAT * 20); // Really hard to configure due  parallel tests
        assertThat(nano.services()).hasSize(2).contains(service);
        assertThat(nano.service(TestService.class)).isEqualTo(service);
        assertThat(nano.services(TestService.class)).hasSize(1).contains(service);
        assertThat(service.startCount()).isEqualTo(1);
        assertThat(service.failures()).isEmpty();
        assertThat(service.stopCount()).isZero();

        // Stop
        waitForCondition(() -> !nano.services().isEmpty(), TEST_TIMEOUT);
        nano.shutdown(nano.context(NanoTest.class)).waitForStop();
        assertThat(nano.isReady()).isFalse();
        assertThat(nano.services()).isEmpty();
        assertThat(nano.listeners()).isEmpty();
//        assertThat(nano.threadPool.isTerminated()).isTrue();
        // assertThat(activeCarrierThreads()).isZero(); Not possible due parallel tests
        assertThat(nano.schedulers()).isEmpty();
        assertThat(service.startCount()).isEqualTo(1);
        assertThat(service.failures()).isEmpty();
        assertThat(service.stopCount()).isEqualTo(1);
        assertThat(nano.waitForStop()).isNotNull().isEqualTo(nano);
    }
}
