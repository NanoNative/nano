package org.nanonative.nano.services.metric.model;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"UnusedReturnValue"})
public class MetricCache {

    private final ConcurrentHashMap<String, Metric<AtomicLong>> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Metric<Double>> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Metric<Long>> timers = new ConcurrentHashMap<>();

    public record Metric<T extends Number>(T value, TreeMap<String, String> tags, String metricName) {
    }

    public Map<String, Metric<AtomicLong>> counters() {
        return counters;
    }

    public Map<String, Metric<Double>> gauges() {
        return gauges;
    }

    public Map<String, Metric<Long>> timers() {
        return timers;
    }

    public Map<String, Metric<? extends Number>> sorted() {
        final TreeMap<String, Metric<? extends Number>> result = new TreeMap<>();
        result.putAll(counters);
        result.putAll(gauges);
        result.putAll(timers);
        return result;
    }

    public MetricCache counterIncrement(final String name) {
        return counterIncrement(name, null);
    }

    public MetricCache counterIncrement(final String name, final Map<String, String> tags) {
        if (name != null) {
            final String id = sanitizeMetricName(name);
            final TreeMap<String, String> sortedTags = new TreeMap<>(tags != null ? tags : emptyMap());
            counters.computeIfAbsent(tags == null ? id : generateUniqueKey(id, sortedTags), key -> new Metric<>(new AtomicLong(), sortedTags, id)).value.incrementAndGet();
        }
        return this;
    }

    public long counter(final String name) {
        return counter(name, null);
    }

    public long counter(final String name, final Map<String, String> tags) {
        final String id = sanitizeMetricName(name);
        return ofNullable(counters.get(tags == null ? id : generateUniqueKey(id, new TreeMap<>(tags)))).map(Metric::value).map(AtomicLong::get).orElse(-1L);
    }

    public MetricCache gaugeSet(final String name, final double value) {
        return gaugeSet(name, value, null);
    }

    public MetricCache gaugeSet(final String name, final double value, final Map<String, String> tags) {
        if (name != null && value > -1) {
            final String id = sanitizeMetricName(name);
            final TreeMap<String, String> sortedTags = new TreeMap<>(tags != null ? tags : emptyMap());
            gauges.put(tags == null ? id : generateUniqueKey(id, sortedTags), new Metric<>(value, sortedTags, id));
        }
        return this;
    }

    public double gauge(final String name) {
        return gauge(name, null);
    }

    public double gauge(final String name, final Map<String, String> tags) {
        final String id = sanitizeMetricName(name);
        return ofNullable(gauges.get(tags == null ? id : generateUniqueKey(id, new TreeMap<>(tags)))).map(Metric::value).orElse(-1d);
    }

    public MetricCache timerStart(final String name) {
        return timerStart(name, null);
    }

    public MetricCache timerStart(final String name, final Map<String, String> tags) {
        if (name != null) {
            final String id = sanitizeMetricName(name);
            final TreeMap<String, String> sortedTags = new TreeMap<>(tags != null ? tags : emptyMap());
            timers.put(tags == null ? id : generateUniqueKey(id, sortedTags), new Metric<>(System.currentTimeMillis(), sortedTags, id));
        }
        return this;
    }

    public MetricCache timerStop(final String name) {
        return timerStop(name, null);
    }

    public MetricCache timerStop(final String name, final Map<String, String> tags) {
        if (name != null) {
            final String id = sanitizeMetricName(name);
            final TreeMap<String, String> sortedTags = new TreeMap<>(tags != null ? tags : emptyMap());
            timers.computeIfPresent(tags == null ? id : generateUniqueKey(id, sortedTags), (key, metric) -> new Metric<>(System.currentTimeMillis() - metric.value, sortedTags, id));
        }
        return this;
    }

    public long timer(final String name) {
        return timer(name, null);
    }

    public long timer(final String name, final Map<String, String> tags) {
        final String id = sanitizeMetricName(name);
        return ofNullable(timers.get(tags == null ? id : generateUniqueKey(id, new TreeMap<>(tags)))).map(Metric::value).orElse(-1L);
    }

    // Adjustments for metric formatting methods to use metric.metricName instead of the unique key
    public String prometheus() {
        final StringBuilder result = new StringBuilder();
        sorted().forEach((id, metric) -> result.append(formatPrometheusMetric(metric)));
        return result.toString();
    }

    // Example adjustment for the InfluxDB format
    public String influx() {
        final StringBuilder sb = new StringBuilder();
        sorted().forEach((id, metric) -> sb.append(metric.metricName()).append(formatInfluxTags(metric.tags())).append(" value=").append(metric.value() instanceof final AtomicLong val ? val.get() : metric.value()).append("\n"));
        return sb.toString();
    }

    public String dynatrace() {
        final StringBuilder sb = new StringBuilder();
        sorted().forEach((id, metric) -> sb.append(formatDynatraceMetric(metric)));
        return sb.toString();
    }

    public String wavefront() {
        final StringBuilder sb = new StringBuilder();
        sorted().forEach((id, metric) -> sb.append(formatWavefrontMetric(metric)));
        return sb.toString();
    }

    // Utility method for generating unique keys remains unchanged
    public String generateUniqueKey(final String name, final Map<String, String> tags) {
        final String tagString = tags.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((t1, t2) -> t1 + "&" + t2).orElse("");
        return name + "{" + tagString + "}";
    }

    public String sanitizeMetricName(final String name) {
        return name == null ? "UNKNOWN.METRIC" : name.replaceAll("[^a-zA-Z0-9.]", ".").replace("..", ".").replaceAll("^\\.|\\.$", "");
    }

    // Adjusted formatting methods to utilize metric.metricName
    private String formatPrometheusMetric(final Metric<?> metric) {
        final String tagsString = metric.tags.entrySet().stream()
            .map(entry -> entry.getKey() + "=\"" + entry.getValue() + "\"")
            .reduce((t1, t2) -> t1 + "," + t2)
            .map(tags -> "{" + tags + "}").orElse("");
        return metric.metricName.replace(".", "_") + tagsString + " " + metric.value + "\n";
    }

    private String formatInfluxTags(final Map<String, String> tags) {
        final StringBuilder tagsBuilder = new StringBuilder();
        tags.forEach((key, value) -> tagsBuilder.append(",").append(key).append("=").append(value));
        return tagsBuilder.toString();
    }

    private String formatDynatraceMetric(final Metric<?> metric) {
        // Adjusting for correct tag formatting in Dynatrace metrics
        final String dimensions = metric.tags.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
        return metric.metricName + "," + dimensions + " " + metric.value + "\n";
    }

    private String formatWavefrontMetric(final Metric<?> metric) {
        // Wavefront format corrected for tag placement
        final String tags = metric.tags.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(" "));
        return metric.metricName + " " + metric.value + " source=nano " + tags + "\n";
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "counters=" + counters.size() +
            ", gauges=" + gauges.size() +
            ", timers=" + timers.size() +
            '}';
    }
}
