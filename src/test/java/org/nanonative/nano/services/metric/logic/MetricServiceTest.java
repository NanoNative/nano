package org.nanonative.nano.services.metric.logic;

import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpService;
import org.nanonative.nano.services.http.model.HttpMethod;
import org.nanonative.nano.services.http.model.HttpObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.model.Context.CONFIG_LOG_LEVEL;
import static org.nanonative.nano.services.metric.logic.MetricService.CONFIG_METRIC_SERVICE_BASE_PATH;
import static org.nanonative.nano.services.metric.logic.MetricService.CONFIG_METRIC_SERVICE_PROMETHEUS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

class MetricServiceTest {

    protected static String serverUrl = "http://localhost:";

    @Test
    void metricEndpointsWithoutBasePath() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new MetricService(), new HttpService());

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpService.class).port() + "/metrics/prometheus")
            .send(nano.context(MetricServiceTest.class));

        assertThat(result).isNotNull();
        assertThat(result.bodyAsString()).contains("java_version ");
        assertThat(nano.stop(MetricServiceTest.class).waitForStop().isReady()).isFalse();

    }

    @Test
    void metricEndpointsWithCustomBasePath() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, CONFIG_METRIC_SERVICE_BASE_PATH, "/custom-metrics"), new MetricService(), new HttpService());

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpService.class).port() + "/custom-metrics/prometheus")
            .send(nano.context(MetricServiceTest.class));

        assertThat(result).isNotNull();
        assertThat(result.bodyAsString()).contains("java_version ");
        assertThat(nano.stop(MetricServiceTest.class).waitForStop().isReady()).isFalse();
    }


    @Test
    void metricEndpointsWithPrometheus() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, CONFIG_METRIC_SERVICE_PROMETHEUS_PATH, "/prometheus"), new MetricService(), new HttpService());

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpService.class).port() + "/prometheus")
            .send(nano.context(MetricServiceTest.class));

        assertThat(result).isNotNull();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(nano.stop(MetricServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void metricEndpointsWithBasePath() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL, CONFIG_METRIC_SERVICE_BASE_PATH, "/stats"), new MetricService(), new HttpService());

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpService.class).port() + "/stats/prometheus")
            .send(nano.context(MetricServiceTest.class));

        assertThat(result).isNotNull();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(nano.stop(MetricServiceTest.class).waitForStop().isReady()).isFalse();

    }

    @Test
    void withoutMetricService() {
        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new HttpService());

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpService.class).port() + "/metrics/prometheus")
            .send(nano.context(MetricServiceTest.class));

        assertThat(result).isNotNull();
        assertThat(result.statusCode()).isEqualTo(404);
        assertThat(nano.stop(MetricServiceTest.class).waitForStop().isReady()).isFalse();

    }
}
