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
import static org.nanonative.nano.core.model.Context.EVENT_APP_SHUTDOWN;
import static org.nanonative.nano.core.model.Context.EVENT_APP_UNHANDLED;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;
import static org.nanonative.nano.helper.event.model.Event.eventOf;
import static org.nanonative.nano.model.TestService.TEST_EVENT;
import static org.nanonative.nano.services.http.HttpService.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;
import static org.nanonative.nano.services.logging.model.LogLevel.INFO;

@Execution(ExecutionMode.CONCURRENT)
class NanoTest {

    @Test
    void configFilesTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano.context().asString(CONFIG_PROFILES)).isEqualTo("default, local, dev, prod");
        assertThat(nano.context().asList(String.class, "_scanned_profiles")).containsExactly("local", "default", "dev", "prod");
        assertThat(nano.context().asString("test_placeholder_fallback")).isEqualTo("fallback should be used 1");
        assertThat(nano.context().asString("test_placeholder_key_empty")).isEqualTo("fallback should be used 2");
        assertThat(nano.context().asString("test_placeholder_value")).isEqualTo("used placeholder value");
        assertThat(nano.context().asString("resource_key1")).isEqualTo("AA");
        assertThat(nano.context().asString("resource_key2")).isEqualTo("CC");
        assertThat(nano.context()).doesNotContainKey("test_placeholder_fallback_empty");
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void stopViaMethod() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano.stop(this.getClass()).waitForStop()).isNotNull().isEqualTo(nano);
    }

    @RepeatedTest(TEST_REPEAT)
    void stopViaEvent() {
        final Context actual = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL)).context(this.getClass()).sendEvent(EVENT_APP_SHUTDOWN, () -> this);
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
        eventOf(nano.context, EVENT_CONFIG_CHANGE).payload(() -> Map.of(CONFIG_LOG_LEVEL, INFO)).send();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(INFO);
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void constructor_withConfigTest() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        assertThat(nano).isNotNull();
        assertThat(nano.context.as(LogLevel.class, CONFIG_LOG_LEVEL)).isEqualTo(TEST_LOG_LEVEL);
        eventOf(nano.context, EVENT_CONFIG_CHANGE).payload(() -> Map.of(CONFIG_LOG_LEVEL, INFO)).broadcast(true).send();
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
        final TestService service = new TestService().doOnEvent(event -> event.ifPresentAck(TEST_EVENT, evt -> {}));
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service);

        // send to first service
        final CountDownLatch latch1 = new CountDownLatch(1);
        nano.sendEvent(eventOf(nano.context(this.getClass()), TEST_EVENT).payload(() -> 11111111).async(response -> latch1.countDown()));
        assertThat(service.getEvent(TEST_EVENT, event -> event.payload(Integer.class) == 11111111)).isNotNull();
        assertThat(latch1.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();

        // send to first listener (listeners have priority)
        service.resetEvents();
        final CountDownLatch latch2 = new CountDownLatch(1);
        nano.subscribeEvent(TEST_EVENT, Event::acknowledge);
        eventOf(nano.context(this.getClass()), TEST_EVENT).payload(() -> 22222222).async(response -> latch2.countDown()).send();
        assertThat(latch2.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThatThrownBy(() -> service.getEvent(TEST_EVENT, 64)).isInstanceOf(AssertionError.class);

        // send to all (listener and services)
        service.resetEvents();
        final CountDownLatch latch3 = new CountDownLatch(1);
        eventOf(nano.context(this.getClass()), TEST_EVENT).payload(() -> 33333333).async(response -> latch3.countDown()).broadcast(true).send();
        assertThat(latch3.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(service.getEvent(TEST_EVENT, event -> event.payload(Integer.class) == 33333333)).isNotNull();

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void sendEventWithEventExecutionException_shouldNotInterrupt() {
        final TestService service = new TestService();
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, LogLevel.OFF), service);

        service.doOnEvent(event -> {
            if (event.channelId() == TEST_EVENT)
                throw new RuntimeException("Nothing to see here, just a test exception");
        });

        final Context context = nano.contextEmpty(this.getClass());
        assertThat(context).hasSize(4).containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY, CONTEXT_PARENT_KEY);

        nano.sendEvent(eventOf(context, TEST_EVENT).payload(() -> 44444444).async(true));
        assertThat(service.getEvent(EVENT_APP_UNHANDLED, event -> event.payload(Integer.class) != null && event.payload(Integer.class) == 44444444)).isNotNull();
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
        final Consumer<Event> listener = event -> {};

        assertThat(nano.listeners().get(TEST_EVENT)).isNull();
        nano.subscribeEvent(TEST_EVENT, listener);
        assertThat(nano.listeners().get(TEST_EVENT)).hasSize(1);
        nano.unsubscribeEvent(TEST_EVENT, listener);
        assertThat(nano.listeners().get(TEST_EVENT)).isEmpty();

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void runSchedulers() throws InterruptedException {
        final long timer = 64;
        final CountDownLatch scheduler1Triggered = new CountDownLatch(1);
        final CountDownLatch scheduler2Triggered = new CountDownLatch(1);
        final CountDownLatch scheduler3Triggered = new CountDownLatch(1);
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));

        nano.run(null, scheduler1Triggered::countDown, timer, MILLISECONDS);
        nano.run(null, scheduler2Triggered::countDown, timer, timer * 2, MILLISECONDS, () -> false);
        nano.run(null, scheduler3Triggered::countDown, LocalTime.now().plusNanos(timer * 1000), LocalDateTime.now().getDayOfWeek(), () -> false);

        assertThat(scheduler1Triggered.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(scheduler2Triggered.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(scheduler3Triggered.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
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
        assertThat(service.getEvent(EVENT_APP_UNHANDLED, event -> event.payload() != null)).isNotNull();
        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void errorHandlerTest() {
        final TestService service = new TestService().doOnEvent(event -> event.ifPresentAck(TEST_EVENT, evt -> {}));
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), service);

        // Execution with error
        nano.context(NanoTest.class).run(() -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });

        assertThat(service.getEvent(EVENT_APP_UNHANDLED)).isNotNull();
        service.resetEvents();

        // Event with error
        nano.subscribeEvent(TEST_EVENT, event -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });

        // Trigger error
        nano.context(NanoTest.class).sendEvent(TEST_EVENT, () -> "test");

        assertThat(service.getEvent(EVENT_APP_UNHANDLED)).isNotNull();
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
