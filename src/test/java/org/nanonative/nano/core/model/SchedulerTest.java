package org.nanonative.nano.core.model;

import org.junit.jupiter.api.RepeatedTest;

import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulerTest {

    @RepeatedTest(TEST_REPEAT)
    void testNewScheduler() {
        final Scheduler scheduler = new Scheduler("test");
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.id()).isEqualTo("test");
        assertThat(scheduler).hasToString("{\"id\":\"test\"}");
    }

}
