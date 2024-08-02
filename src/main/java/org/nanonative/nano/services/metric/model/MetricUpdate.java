package org.nanonative.nano.services.metric.model;

import java.util.Map;

public record MetricUpdate(MetricType type, String name, Number value, Map<String, String> tags) {
}
