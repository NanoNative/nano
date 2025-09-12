package org.nanonative.nano.core.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.model.TestService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.nanonative.nano.core.config.TestConfig.TEST_TIMEOUT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_ERROR;
import static org.nanonative.nano.helper.NanoUtils.waitForCondition;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Execution(ExecutionMode.CONCURRENT)
class ServiceTest {

    @RepeatedTest(TEST_REPEAT)
    void testService() {
        final long startTime = System.nanoTime();
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL));
        final Context context = nano.context(this.getClass());
        final TestService service = new TestService();
        final Event<Object, Object> errorEvent = context.newEvent(new Channel<>(999, "unknown", Object.class, Object.class)).payload(() -> "TEST ERROR_AA").error(new RuntimeException("TEST ERROR_BB"));

        assertThat(service).isNotNull();
        assertThat(service.createdAtNs()).isGreaterThan(startTime);
        assertThat(service.startCount()).isZero();
        assertThat(service.stopCount()).isZero();
        assertThat(service.events()).isEmpty();
        assertThat(service.failures()).isEmpty();

        service.start();
        assertThat(service.startCount()).isEqualTo(1);

        service.stop();
        assertThat(service.stopCount()).isEqualTo(1);

        service.onFailure(errorEvent);
        assertThat(service.failures()).hasSize(1).contains(errorEvent);

        final Event<Object, Void> event = context.newEvent(EVENT_APP_ERROR).payload(() -> Map.of("myKey", "myValue")).send();
        service.onEvent(event);
        final Event<Object, Void> receivedEvent = service.getEvent(EVENT_APP_ERROR);
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.payload()).isEqualTo(Map.of("myKey", "myValue"));

        assertThat(waitForCondition(() -> nano.services().size() == 1, TEST_TIMEOUT)).isTrue();
        service.nanoThread(context).run(() -> context, () -> {});
        assertThat(waitForCondition(() -> service.startCount() == 2, TEST_TIMEOUT)).isTrue();
        waitForCondition(() -> nano.services().size() == 2, TEST_TIMEOUT);
        assertThat(service.startCount()).isEqualTo(2);
        assertThat(service.failures()).hasSize(1);
        assertThat(nano.services()).size().isEqualTo(2);

        assertThat(nano.stop(this.getClass()).waitForStop().isReady()).isFalse();
    }
}
