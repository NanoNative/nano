package org.nanonative.nano.services.metric.model;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;

import static org.nanonative.nano.core.config.TestConfig.TEST_REPEAT;
import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class MetricCacheTest {


    @RepeatedTest(TEST_REPEAT)
    void generateMetricFormats() throws InterruptedException {
        final MetricCache metricCache = new MetricCache()
            .counterIncrement("my_counter")
            .counterIncrement("my/counter")
            .gaugeSet("my_gauge", 100)
            .gaugeSet("my$gauge", 9.99)
            .timerStart("my^timer");
        Thread.sleep(15);
        metricCache.timerStop("my#timer");

        assertThat(metricCache.counters()).hasSize(1);
        assertThat(metricCache.gauges()).hasSize(1);
        assertThat(metricCache.timers()).hasSize(1);

        final long timer = metricCache.timer("my%timer");
        assertThat(metricCache.counter("my%counter")).isEqualTo(2);
        assertThat(metricCache.gauge("my%gauge")).isEqualTo(9.99);
        assertThat(timer).isBetween(15L, 60L);
        assertThat(metricCache.prometheus()).isEqualTo("my_counter 2\nmy_gauge 9.99\nmy_timer " + timer + "\n");
        assertThat(metricCache.influx()).isEqualTo("my.counter value=2\nmy.gauge value=9.99\nmy.timer value=" + timer + "\n");
        assertThat(metricCache.dynatrace()).isEqualTo("my.counter, 2\nmy.gauge, 9.99\nmy.timer, " + timer + "\n");
        assertThat(metricCache.wavefront()).isEqualTo("my.counter 2 source=nano \nmy.gauge 9.99 source=nano \nmy.timer " + timer + " source=nano \n");
        assertThat(metricCache).hasToString(MetricCache.class.getSimpleName() + "{counters=1, gauges=1, timers=1}");
    }

    @RepeatedTest(TEST_REPEAT)
    void generateMetricFormatsWithTags() throws InterruptedException {
        final Map<String, String> tags = Map.of("aa", "bb", "cc", "dd");
        final MetricCache metricCache = new MetricCache()
            .counterIncrement("my_counter", tags)
            .counterIncrement("my/counter", tags)
            .gaugeSet("my_gauge", 100, tags)
            .gaugeSet("my$gauge", 9.99, tags)
            .timerStart("my^timer", tags);
        Thread.sleep(15);
        metricCache.timerStop("my#timer", tags);

        assertThat(metricCache.counters()).hasSize(1);
        assertThat(metricCache.gauges()).hasSize(1);
        assertThat(metricCache.timers()).hasSize(1);

        final long timer = metricCache.timer("my%timer", tags);
        assertThat(timer).isBetween(15L, 60L);
        assertThat(metricCache.counter("my%counter", tags)).isEqualTo(2);
        assertThat(metricCache.gauge("my%gauge", tags)).isEqualTo(9.99);
        assertThat(metricCache.prometheus()).isEqualTo("my_counter{aa=\"bb\",cc=\"dd\"} 2\nmy_gauge{aa=\"bb\",cc=\"dd\"} 9.99\nmy_timer{aa=\"bb\",cc=\"dd\"} " + timer + "\n");
        assertThat(metricCache.influx()).isEqualTo("my.counter,aa=bb,cc=dd value=2\nmy.gauge,aa=bb,cc=dd value=9.99\nmy.timer,aa=bb,cc=dd value=" + timer + "\n");
        assertThat(metricCache.dynatrace()).isEqualTo("my.counter,aa=bb,cc=dd 2\nmy.gauge,aa=bb,cc=dd 9.99\nmy.timer,aa=bb,cc=dd " + timer + "\n");
        assertThat(metricCache.wavefront()).isEqualTo("my.counter 2 source=nano aa=bb cc=dd\nmy.gauge 9.99 source=nano aa=bb cc=dd\nmy.timer " + timer + " source=nano aa=bb cc=dd\n");
        assertThat(metricCache).hasToString(MetricCache.class.getSimpleName() + "{counters=1, gauges=1, timers=1}");
    }
}
