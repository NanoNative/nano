package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.helper.logger.model.LogLevel;
import org.nanonative.nano.services.http.HttpService;
import org.nanonative.nano.services.metric.logic.MetricService;

import java.util.Map;

import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_FORMATTER;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Disabled
public class Kazim {

    public static void main(String[] args) {
        final Nano application = new Nano(Map.of(
            CONFIG_LOG_LEVEL, LogLevel.INFO,
            CONFIG_LOG_FORMATTER, "console"
//            CONFIG_METRIC_SERVICE_BASE_PATH, "/metrics",
//            CONFIG_METRIC_SERVICE_PROMETHEUS_PATH, "/influx"
        ), new MetricService(), new HttpService());

    }
}
