package org.nanonative.nano.core.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.config.TestConfig;
import org.nanonative.nano.helper.event.EventChannelRegister;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.model.TestService;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.Context.CONTEXT_CLASS_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_NANO_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_TRACE_ID_KEY;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@SuppressWarnings("java:S5778")
@Execution(ExecutionMode.CONCURRENT)
class ContextTest {

    private static final int TEST_CHANNEL_ID = EventChannelRegister.registerChannelId("TEST_EVENT");

    @RepeatedTest(TEST_REPEAT)
    void testNewContext_withNano() throws InterruptedException {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TestConfig.TEST_LOG_LEVEL));
        final Context context = new Context(null, nano.getClass()).put(CONTEXT_NANO_KEY, nano);
        final Consumer<Event> myListener = event -> {};
        assertContextBehaviour(context);

        // Verify event listener
        assertThat(nano.listeners().get(EVENT_APP_HEARTBEAT)).hasSize(1);
        assertThat(context.subscribeEvent(EVENT_APP_HEARTBEAT, myListener)).isEqualTo(context);
        assertThat(nano.listeners().get(EVENT_APP_HEARTBEAT)).hasSize(2);
        assertThat(context.unsubscribeEvent(EVENT_APP_HEARTBEAT, myListener)).isEqualTo(context);
        assertThat(nano.listeners().get(EVENT_APP_HEARTBEAT)).hasSize(1);

        // Verify event sending
        final CountDownLatch eventLatch = new CountDownLatch(4);
        final int channelId = context.registerChannelId("TEST_EVENT");
        context.subscribeEvent(channelId, event -> eventLatch.countDown());
        context.sendEvent(channelId, () -> "AA");
        final Event event = context.sendEventR(channelId, () -> "BB");
        context.broadcastEvent(channelId, () -> "CC");
        context.broadcastEventR(channelId, () -> "DD");
        assertThat(event).isNotNull();
        assertThat(event.payload()).isEqualTo("BB");
        assertThat(event.channel()).isEqualTo("TEST_EVENT");
        assertThat(event.channelId()).isEqualTo(channelId);
        assertThat(event.context()).isEqualTo(context);
        assertThat(event.isAcknowledged()).isFalse();
        assertThat(eventLatch.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(eventLatch.getCount()).isZero();
        assertThat(channelId).isEqualTo(TEST_CHANNEL_ID);

        // Verify services
        final TestService testService = new TestService();
        assertThat(context.run(testService)).isEqualTo(context);
        assertThat(waitForCondition(() -> context.services().contains(testService), TEST_TIMEOUT)).isTrue();
        assertThat(context.service(testService.getClass())).isEqualTo(testService);
        assertThat(context.services(TestService.class)).containsExactly(testService);

        // Verify schedule once
        final CountDownLatch latch1 = new CountDownLatch(1);
        context.run(latch1::countDown, 16, MILLISECONDS);
        assertThat(latch1.await(TEST_TIMEOUT, MILLISECONDS))
            .withFailMessage("latch1 \nExpected: 1 \n Actual: " + latch1.getCount())
            .isTrue();

        // Verify schedule multiple time with stop
        final CountDownLatch latch2 = new CountDownLatch(4);
        context.run(latch2::countDown, 0, 16, MILLISECONDS);
        assertThat(latch2.await(TEST_TIMEOUT, MILLISECONDS))
            .withFailMessage("latch2 \nExpected: 4 \n Actual: " + latch2.getCount())
            .isTrue();

        final CountDownLatch latch3 = new CountDownLatch(1);
        context.run(latch3::countDown, LocalTime.now().plus(16, MILLIS));
        assertThat(latch3.await(TEST_TIMEOUT, MILLISECONDS))
            .withFailMessage("latch3 \nExpected: 1 \n Actual: " + latch3.getCount())
            .isTrue();

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    @RepeatedTest(TEST_REPEAT)
    void testNewContext_withoutNano() {
        final Context context = Context.createRootContext(ContextTest.class);
        final Consumer<Event> myListener = event -> {};
        assertContextBehaviour(context);
        assertThatThrownBy(() -> context.subscribeEvent(EVENT_APP_HEARTBEAT, myListener)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.unsubscribeEvent(EVENT_APP_HEARTBEAT, myListener)).isInstanceOf(NullPointerException.class);
    }

    @RepeatedTest(TEST_REPEAT)
    void testNewEmptyContext_withoutClass_willCreateRootContext() {
        final Context context = Context.createRootContext(ContextTest.class);
        assertContextBehaviour(context);
        final Context subContext = context.newContext(null);
        assertThat(subContext.traceId()).startsWith(ContextTest.class.getSimpleName() + "/");
    }

    @RepeatedTest(TEST_REPEAT)
    void testRunHandled_withException() throws InterruptedException {
        final Context context = Context.createRootContext(ContextTest.class);
        final CountDownLatch latch = new CountDownLatch(2);
        assertContextBehaviour(context);
        assertThat(context.runHandled(unhandled -> latch.countDown(), () -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        })).isEqualTo(context);
        assertThat(context.runReturnHandled(unhandled -> latch.countDown(), () -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        })).isNotNull();
        assertThat(latch.await(1000, MILLISECONDS)).isTrue();
        assertThat(latch.getCount()).isZero();
    }


    @RepeatedTest(TEST_REPEAT)
    void testRunAwaitHandled_withException() throws InterruptedException {
        final Context context = Context.createRootContext(ContextTest.class);
        final CountDownLatch latch = new CountDownLatch(1);
        assertContextBehaviour(context);
        context.runAwaitHandled(unhandled -> latch.countDown(), () -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });
        assertThat(latch.await(1000, MILLISECONDS)).isTrue();
        assertThat(latch.getCount()).isZero();
    }

    @RepeatedTest(TEST_REPEAT)
    void testRunAwait_withException() {
        final Context context = Context.createRootContext(ContextTest.class);
        assertContextBehaviour(context);
        context.runAwait(() -> {
            throw new RuntimeException("Nothing to see here, just a test exception");
        });
        //TODO: create an unhandled element and check if the error was unhandled
    }

    @RepeatedTest(TEST_REPEAT)
    void testEventSubscription() throws InterruptedException {
        final Nano app = new Nano();
        final Context context = app.context(this.getClass());
        final AtomicInteger counter = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(2);
        context.subscribeEvent(TEST_CHANNEL_ID, event -> latch.countDown()); // MATCH
        context.subscribeEvent(TEST_CHANNEL_ID, TestService.class, (event, testService) -> counter.incrementAndGet()); // NO MATCH
        context.subscribeEvent(TEST_CHANNEL_ID, Integer.class, (event, testService) -> latch.countDown()); // MACH
        context.newEvent(TEST_CHANNEL_ID).payload(() -> 200888).send();
        assertThat(counter.get()).isZero();
        assertThat(latch.await(TEST_TIMEOUT, MILLISECONDS)).isTrue();
        assertThat(app.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }

    private void assertContextBehaviour(final Context context) {
        assertThat(context)
            .hasSize(3)
            .containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY);

        context.put("AA", "BB");
        assertThat(context)
            .hasSize(4)
            .containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY, CONTEXT_TRACE_ID_KEY)
            .containsKey("AA");

        assertThat(context.newContext(this.getClass()))
            .hasSize(5)
            .containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY, CONTEXT_TRACE_ID_KEY)
            .containsKey("AA");

        assertThat(context.newContext(this.getClass()))
            .hasSize(5)
            .containsKeys(CONTEXT_NANO_KEY, CONTEXT_TRACE_ID_KEY, CONTEXT_CLASS_KEY, CONTEXT_TRACE_ID_KEY);

        //Verify trace id is shared between contexts
        assertThat(context.newContext(this.getClass()).asList(CONTEXT_TRACE_ID_KEY)).hasSize(1).doesNotContain(context.traceId());
        final Context subContext = context.newContext(this.getClass());
        assertThat(subContext.asList(CONTEXT_TRACE_ID_KEY)).hasSize(1).doesNotContain(context.traceId());
        assertThat(subContext.traceId()).isNotEqualTo(context.traceId());
        assertThat(subContext.traceId(0)).isEqualTo(subContext.traceId()).isNotEqualTo(context.traceId());
        assertThat(subContext.traceId(1)).isNotEqualTo(subContext.traceId()).isEqualTo(context.traceId());
        assertThat(subContext.traceId(99)).isEqualTo(subContext.traceId()).isNotEqualTo(context.traceId());
        assertThat(subContext.traceIds()).containsExactlyInAnyOrder(context.traceId(), subContext.traceId());
    }

    @RepeatedTest(TEST_REPEAT)
    void testToString() {
        final Context context = Context.createRootContext(ContextTest.class);
        assertThat(context).hasToString("{\"size\":" + context.size() + ",\"class\":\"" + this.getClass().getSimpleName() + "\"}");
    }

}
