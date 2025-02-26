package org.nanonative.nano.core.model;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.config.TestConfig;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.model.TestService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_UNHANDLED;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Execution(ExecutionMode.CONCURRENT)
class ServiceTest {

    @RepeatedTest(TestConfig.TEST_REPEAT)
    void testService() {
        final long startTime = System.currentTimeMillis() - 10;
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TestConfig.TEST_LOG_LEVEL));
        final Context context = nano.context(this.getClass());
        final TestService service = new TestService();
        final Event error = new Event(999, context, "TEST ERROR_AA", null).error(new RuntimeException("TEST ERROR_BB"));

        assertThat(service).isNotNull();
        assertThat(service.createdAtMs()).isGreaterThan(startTime);
        assertThat(service.startCount()).isZero();
        assertThat(service.stopCount()).isZero();
        assertThat(service.events()).isEmpty();
        assertThat(service.failures()).isEmpty();

        service.start();
        assertThat(service.startCount()).isEqualTo(1);

        service.stop();
        assertThat(service.stopCount()).isEqualTo(1);

        service.onFailure(error);
        assertThat(service.failures()).hasSize(1).contains(error);

        final Event event = new Event(EVENT_APP_UNHANDLED, context, error, null);
        service.onEvent(event);
        assertThat(service.getEvent(EVENT_APP_UNHANDLED)).isNotNull().has(new Condition<>(e -> e.payload(Event.class) == error, "Should contain payload with error"));

        assertThat(nano.services()).isEmpty();
        service.nanoThread(context).run(null, () -> context, () -> {});
        assertThat(waitForCondition(() -> service.startCount() == 2, TEST_TIMEOUT)).isTrue();
        waitForCondition(() -> nano.services().size() == 1, TEST_TIMEOUT);
        assertThat(service.startCount()).isEqualTo(2);
        assertThat(service.failures()).hasSize(1);
        assertThat(nano.services()).size().isEqualTo(1);

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }
}
