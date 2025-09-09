package org.nanonative.nano.services.metric.logic;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpHeaders;
import org.nanonative.nano.services.logging.model.LogLevel;
import org.nanonative.nano.services.metric.model.MetricCache;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.io.File;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.helper.NanoUtils.tryExecute;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;


@SuppressWarnings({"unused", "UnusedReturnValue"})
public class MetricService extends Service {
    private final MetricCache metrics = new MetricCache();
    protected String prometheusPath;
    protected String dynamoPath;
    protected String influx;
    protected String wavefront;

    // Register configurations
    public static final String CONFIG_METRIC_SERVICE_BASE_PATH = registerConfig("app_service_metrics_base_url", "Base path for the metric service");
    public static final String CONFIG_METRIC_SERVICE_PROMETHEUS_PATH = registerConfig("app_service_prometheus_metrics_url", "Prometheus path for the metric service");
    public static final String CONFIG_METRIC_SERVICE_INFLUX_PATH = registerConfig("app_service_influx_metrics_url", "Influx path for the metric service");
    public static final String CONFIG_METRIC_SERVICE_WAVEFRONT_PATH = registerConfig("app_service_wavefront_metrics_url", "Wavefront path for the metric service");
    public static final String CONFIG_METRIC_SERVICE_DYNAMO_PATH = registerConfig("app_service_dynamo_metrics_url", "Dynamo path for the metric service");

    // Register event channels
    public static final Channel<MetricUpdate, Void> EVENT_METRIC_UPDATE = registerChannelId("EVENT_METRIC_UPDATE", MetricUpdate.class);

    @Override
    public void start() {
        updateSystemMetrics(context);
    }

    @Override
    public void stop() {
        metrics.gauges().clear();
        metrics.timers().clear();
        metrics.counters().clear();
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return null;
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        event.channel(EVENT_APP_HEARTBEAT).ifPresent(e -> {
            e.acknowledge();
            updateMetrics(context.nano());
        });
        event.channel(EVENT_METRIC_UPDATE).map(Event::payloadAck).map(this::updateMetric);
        addMetricsEndpoint(event);

    }

    @Override
    public void configure(final TypeMapI<?> configs, final TypeMapI<?> merged) {
        final Optional<String> basePath = Optional.of(merged.asStringOpt(CONFIG_METRIC_SERVICE_BASE_PATH).orElseGet(() -> "/metrics"));

        prometheusPath = merged.asStringOpt(CONFIG_METRIC_SERVICE_PROMETHEUS_PATH).orElseGet(() -> basePath.map(base -> base + "/prometheus").orElse(null));
        dynamoPath = merged.asStringOpt(CONFIG_METRIC_SERVICE_DYNAMO_PATH).orElseGet(() -> basePath.map(base -> base + "/dynamo").orElse(null));
        influx = merged.asStringOpt(CONFIG_METRIC_SERVICE_INFLUX_PATH).orElseGet(() -> basePath.map(base -> base + "/influx").orElse(null));
        wavefront = merged.asStringOpt(CONFIG_METRIC_SERVICE_WAVEFRONT_PATH).orElseGet(() -> basePath.map(base -> base + "/wavefront").orElse(null));
        configs.asOpt(LogLevel.class, CONFIG_LOG_LEVEL).ifPresent(level -> metrics.gaugeSet("logger", 1, Map.of("level", level.name())));
    }

    protected void addMetricsEndpoint(final Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).map(Event::payload).ifPresent(request ->
            ofNullable(prometheusPath)
                .filter(request::pathMatch)
                .filter(path -> request.isMethodGet())
                .ifPresent(path ->
                    request.createResponse()
                        .statusCode(200)
                        .body(metrics.prometheus())
                        .headerMap(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN))
                        .respond(event)
                )
        );
        event.channel(EVENT_HTTP_REQUEST).map(Event::payload).ifPresent(request ->
            ofNullable(dynamoPath)
                .filter(request::pathMatch)
                .filter(path -> request.isMethodGet())
                .ifPresent(path ->
                    request.createResponse()
                        .statusCode(200)
                        .body(metrics.dynatrace())
                        .headerMap(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN))
                        .respond(event)
                )
        );
        event.channel(EVENT_HTTP_REQUEST).map(Event::payload).ifPresent(request ->
            ofNullable(influx)
                .filter(request::pathMatch)
                .filter(path -> request.isMethodGet())
                .ifPresent(path ->
                    request.createResponse()
                        .statusCode(200)
                        .body(metrics.influx())
                        .headerMap(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN))
                        .respond(event)
                )
        );
        event.channel(EVENT_HTTP_REQUEST).map(Event::payload).ifPresent(request ->
            ofNullable(wavefront)
                .filter(request::pathMatch)
                .filter(path -> request.isMethodGet())
                .ifPresent(path ->
                    request.createResponse()
                        .statusCode(200)
                        .body(metrics.wavefront())
                        .headerMap(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN))
                        .respond(event)
                )
        );
    }

    @SuppressWarnings("SameReturnValue")
    public boolean updateMetric(final MetricUpdate metric) {
        switch (metric.type()) {
            case GAUGE -> metrics.gaugeSet(metric.name(), metric.value().doubleValue(), metric.tags());
            case COUNTER -> metrics.counterIncrement(metric.name(), metric.tags());
            case TIMER_START -> metrics.timerStart(metric.name(), metric.tags());
            case TIMER_END -> metrics.timerStop(metric.name(), metric.tags());
        }
        return true;
    }

    public MetricCache metrics() {
        return metrics;
    }

    public MetricService updateMetrics(final Nano nano) {
        updateNanoMetrics(nano);
        updateCpuMetrics(nano::context);
        updateDiscMetrics(nano::context);
        updateMemoryMetrics(nano::context);
        updatePoolMetrics(nano::context);
        updateThreadMetrics(nano::context);
        updateBufferMetrics(nano::context);
        updateClassLoaderMetrics(nano::context);
        updateCompilerMetrics(nano);
        nano.context().tryExecute(() -> {
            metrics.gaugeSet("service.metrics.gauges", metrics.gauges().size());
            metrics.gaugeSet("service.metrics.timers", metrics.timers().size());
            metrics.gaugeSet("service.metrics.counters", metrics.counters().size());
            metrics.gaugeSet("service.metrics.bytes", estimateMetricCacheSize());
        });
        return this;
    }

    public void updateCompilerMetrics(final Nano nano) {
        nano.context().tryExecute(() -> {
            final CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
            if (compilationMXBean.isCompilationTimeMonitoringSupported()) {
                metrics.gaugeSet("jvm.compilation.time.ms", compilationMXBean.getTotalCompilationTime());
            }
        });
    }

    public void updateClassLoaderMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
            metrics.gaugeSet("jvm.classes.loaded", classLoadingMXBean.getLoadedClassCount());
            metrics.gaugeSet("jvm.classes.unloaded", classLoadingMXBean.getUnloadedClassCount());
        });
    }

    public void updateBufferMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final String suffix = ".bytes";
            for (final BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                final Map<String, String> tags = new HashMap<>();
                tags.put("id", pool.getName());
                metrics.gaugeSet("jvm.buffer.count." + pool.getName() + suffix, pool.getCount(), tags);
                metrics.gaugeSet("jvm.buffer.memory.used." + pool.getName() + suffix, pool.getMemoryUsed(), tags);
                metrics.gaugeSet("jvm.buffer.total.capacity." + pool.getName() + suffix, pool.getTotalCapacity(), tags);
            }
        });
    }

    public void updateThreadMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            metrics.gaugeSet("jvm.threads.live", Thread.activeCount());
            metrics.gaugeSet("jvm.threads.nano", NanoThread.activeNanoThreads());
            metrics.gaugeSet("jvm.threads.carrier", NanoThread.activeCarrierThreads());
        });
        tryExecute(context, () -> {
            final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            metrics.gaugeSet("jvm.threads.daemon", threadMXBean.getDaemonThreadCount());
            metrics.gaugeSet("jvm.threads.live", threadMXBean.getThreadCount());
            metrics.gaugeSet("jvm.threads.peak", threadMXBean.getPeakThreadCount());

            Arrays.stream(threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ThreadInfo::getThreadState, Collectors.counting()))
                .forEach((state, count) -> metrics.gaugeSet("jvm.threads.states", count, Map.of("state", state.toString().toLowerCase())));
        });
    }

    public void updatePoolMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (final MemoryPoolMXBean pool : pools) {
                final String area = pool.getType() == MemoryType.HEAP ? "heap" : "nonheap";
                final MemoryUsage usage = pool.getUsage();

                metrics.gaugeSet("jvm.memory.max.bytes", usage.getMax(), Map.of("area", area, "id", pool.getName()));
                metrics.gaugeSet("jvm.memory.used.bytes", usage.getUsed(), Map.of("area", area, "id", pool.getName()));
                metrics.gaugeSet("jvm.memory.committed.bytes", usage.getCommitted(), Map.of("area", area, "id", pool.getName()));
            }
        });
    }

    public void updateMemoryMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final Runtime runtime = Runtime.getRuntime();
            metrics.gaugeSet("system.cpu.cores", runtime.availableProcessors());
            metrics.gaugeSet("jvm.memory.max.bytes", runtime.maxMemory());
            metrics.gaugeSet("jvm.memory.used.bytes", (double) runtime.totalMemory() - runtime.freeMemory());
        });
    }

    public void updateDiscMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final File disk = new File("/");
            metrics.gaugeSet("disk.free.bytes", disk.getFreeSpace());
            metrics.gaugeSet("disk.total.bytes", disk.getTotalSpace());
        });
    }

    public void updateCpuMetrics(final Supplier<Context> context) {
        tryExecute(context, () -> {
            final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
            if (osMXBean instanceof final com.sun.management.OperatingSystemMXBean sunOsMXBean) {
                metrics.gaugeSet("process.cpu.usage", sunOsMXBean.getProcessCpuLoad());
                metrics.gaugeSet("system.cpu.usage", sunOsMXBean.getCpuLoad());
            }
            metrics.gaugeSet("system.load.average.1m", osMXBean.getSystemLoadAverage());
        });
    }

    public void updateNanoMetrics(final Nano nano) {
        tryExecute(nano::context, () -> {
            nano.services().stream()
                .collect(Collectors.groupingBy(service -> service.getClass().getSimpleName(), Collectors.counting()))
                .forEach((className, count) -> metrics.gaugeSet("application.services", count, Map.of("class", className)));
            metrics.gaugeSet("application.schedulers", nano.schedulers().size());
            metrics.gaugeSet("application.listeners", nano.listeners().size());
        });
    }

    public void updateSystemMetrics(final Context context) {
        final String numberRegex = "\\D";
        metrics.gaugeSet("application.pid", ProcessHandle.current().pid());
        updateJavaVersion(context);
        updateArch(context);
        updateOs(context);
        tryExecute(() -> context, () -> metrics.gaugeSet("system.version", Double.parseDouble(System.getProperty("os.version").replaceAll(numberRegex, ""))));
    }

    public void updateOs(final Context context) {
        tryExecute(() -> context, () -> {
            String osName = System.getProperty("os.name");
            osName = osName == null ? "" : osName.toLowerCase();
            final List<String> osPrefixes = List.of("linux", "mac", "windows", "aix", "irix", "hp-ux", "os/400", "freebsd", "openbsd", "netbsd", "os/2", "solaris", "sunos", "mips", "z/os");
            final List<String> unix = List.of("linux", "mac", "aix", "irix", "hp-ux", "freebsd", "openbsd", "netbsd", "solaris", "sunos");

            final String finalOsName = osName;
            metrics.gaugeSet("system.payload", IntStream.range(0, osPrefixes.size()).filter(i -> finalOsName.startsWith(osPrefixes.get(i))).findFirst().orElse(-1) + 1d);
            metrics.gaugeSet("system.unix", unix.stream().anyMatch(finalOsName::startsWith) ? 1 : 0);
        });
    }

    public void updateArch(final Context context) {
        tryExecute(() -> context, () -> {
            final String metricName = "system.arch.bit";
            String arch = System.getProperty("os.arch");
            arch = arch == null ? "" : arch.toLowerCase();
            if (arch.contains("64")) {
                metrics.gaugeSet(metricName, 64);
            } else if (Stream.of("x86", "686", "386", "368").anyMatch(arch::contains)) {
                metrics.gaugeSet(metricName, 32);
            } else {
                final String number = arch.replaceAll("\\D", "");
                metrics.gaugeSet(metricName, number.isEmpty() ? 0 : Double.parseDouble(number));
            }
        });
    }

    public void updateJavaVersion(final Context context) {
        tryExecute(() -> context, () -> {
            String version = System.getProperty("java.version");
            version = version.startsWith("1.") ? version.substring(2) : version;
            version = version.contains(".") ? version.substring(0, version.indexOf(".")) : version;
            metrics.gaugeSet("java.version", Double.parseDouble(version.replaceAll("\\D", "")));
        });
    }

    public long estimateMetricCacheSize() {
        long totalSize = 0;
        // Calculate size for counters, gauges, and timers
        totalSize += estimateMapSize(new HashMap<>(metrics.counters()), 28) +
            estimateMapSize(new HashMap<>(metrics.gauges()), 24) +
            estimateMapSize(new HashMap<>(metrics.timers()), 16);

        return totalSize;
    }

    private long estimateMapSize(final Map<String, MetricCache.Metric<?>> map, final long numberSize) {
        long size = 36L;
        for (final Map.Entry<String, MetricCache.Metric<?>> entry : map.entrySet()) {
            size += 32L + // Entry overhead
                estimateStringSize(entry.getKey()) + // Key size
                estimateMetricSize(entry.getValue(), numberSize); // Value size
        }
        return size;
    }

    private long estimateMetricSize(final MetricCache.Metric<?> metric, final long numberSize) {
        long size = 48; // TreeMap overhead for tags
        size += estimateStringSize(metric.metricName()); // Metric name size
        size += numberSize; // Number size (AtomicLong, Double, Long)
        for (final Map.Entry<String, String> tag : metric.tags().entrySet()) {
            size += estimateStringSize(tag.getKey()) + estimateStringSize(tag.getValue()); // Tag key-value sizes
        }
        return size;
    }

    private long estimateStringSize(final String string) {
        return 24 + (long) string.length() * 2; // String object overhead + 2 bytes per character
    }

    public String prometheusPath() {
        return prometheusPath;
    }

    public String dynamoPath() {
        return dynamoPath;
    }

    public String influx() {
        return influx;
    }

    public String wavefront() {
        return wavefront;
    }
}
