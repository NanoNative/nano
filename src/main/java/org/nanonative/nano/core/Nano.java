package org.nanonative.nano.core;

import berlin.yuna.typemap.model.FunctionOrNull;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Scheduler;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.file.FileChangeEvent;
import org.nanonative.nano.services.file.FileWatchRequest;
import org.nanonative.nano.services.file.FileWatcher;
import org.nanonative.nano.services.logging.LogService;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.nanonative.nano.core.model.Context.APP_NANO_NAME;
import static org.nanonative.nano.core.model.Context.APP_PARAMS;
import static org.nanonative.nano.core.model.Context.CONFIG_ENV_PROD;
import static org.nanonative.nano.core.model.Context.CONFIG_OOM_SHUTDOWN_THRESHOLD;
import static org.nanonative.nano.core.model.Context.CONTEXT_CLASS_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_NANO_KEY;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_OOM;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SHUTDOWN;
import static org.nanonative.nano.core.model.Context.EVENT_APP_START;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.helper.NanoUtils.generateNanoName;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_WATCH;
import static org.nanonative.nano.services.logging.LogService.EVENT_LOGGING;
import static org.nanonative.nano.services.metric.logic.MetricService.EVENT_METRIC_UPDATE;
import static org.nanonative.nano.services.metric.model.MetricType.GAUGE;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Nano extends NanoServices<Nano> {

    /**
     * Initializes {@link Nano} with a set of startup {@link Service}.
     *
     * @param startupServices Varargs parameter of startup {@link Service} to be initiated during the {@link Nano} creation.
     */
    public Nano(final Service... startupServices) {
        this((String[]) null, startupServices);
    }

    /**
     * Initializes {@link Nano} with a function to provide startup {@link Service} based on the context.
     *
     * @param args            Command-line arguments passed during the application start.
     * @param startupServices Varargs parameter of startup {@link Service} to be initiated.
     */
    public Nano(final String[] args, final Service... startupServices) {
        this(context -> asList(startupServices), null, args);
    }

    /**
     * Initializes  {@link Nano} with configurations and startup {@link Service}.
     *
     * @param config          Map of configuration parameters.
     * @param startupServices Varargs parameter of startup {@link Service} to be initiated.
     */
    public Nano(final Map<Object, Object> config, final Service... startupServices) {
        this(ctx -> List.of(startupServices), config);
    }

    /**
     * Initializes  {@link Nano} with configurations and startup {@link Service}.
     *
     * @param config          Map of configuration parameters.
     * @param startupServices Function to provide startup {@link Service} based on the given context.
     */
    public Nano(final Map<Object, Object> config, final FunctionOrNull<Context, List<Service>> startupServices) {
        this(startupServices, config);
    }

    /**
     * Initializes {@link Nano} with a function to provide startup {@link Service} based on the context.
     *
     * @param args            Command-line arguments passed during the application start.
     * @param startupServices Function to provide startup {@link Service} based on the given context.
     */
    public Nano(final String[] args, final FunctionOrNull<Context, List<Service>> startupServices) {
        this(startupServices, null, args);
    }

    /**
     * Initializes {@link Nano} with a function to provide startup {@link Service}, configurations, and command-line arguments.
     *
     * @param startupServices Function to provide startup {@link Service} based on the given context.
     * @param config          Map of configuration parameters.
     * @param args            Command-line arguments passed during the application start.
     */
    public Nano(final FunctionOrNull<Context, List<Service>> startupServices, final Map<Object, Object> config, final String... args) {
        super(config, args);
        // INIT CONTEXT
        context.put(CONTEXT_NANO_KEY, this);
        context.put(CONTEXT_CLASS_KEY, this.getClass());
        final long initTime = System.nanoTime() - createdAtNs;
        context.trace(() -> "Init [{}] in [{}]", this.getClass().getSimpleName(), NanoUtils.formatDuration(initTime));
        printParameters();

        final long service_startUpTime = System.nanoTime();
        // INIT SHUTDOWN HOOK
        try {
            // java.lang.IllegalStateException: Shutdown in progress
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(context(this.getClass()))));
            startServicesAndLogger(startupServices);
            context.computeIfAbsent(APP_NANO_NAME, k -> generateNanoName("%s%.0s%.0s%.0s"));
            run(() -> context, () -> context.newEvent(EVENT_APP_HEARTBEAT).async(true).send(), 256, 256, MILLISECONDS, () -> false);
            run(() -> context, System::gc, 5, 5, SECONDS, () -> false);
            final long readyTime = System.nanoTime() - service_startUpTime;
            printActiveProfiles();
            context.info(() -> "Started [{}] in [{}]", context.asString(APP_NANO_NAME), NanoUtils.formatDuration(readyTime));
            printSystemInfo();

            context.newEvent(EVENT_METRIC_UPDATE, () -> new MetricUpdate(GAUGE, "application.started.time", initTime, null)).async(true).send();
            context.newEvent(EVENT_METRIC_UPDATE, () -> new MetricUpdate(GAUGE, "application.ready.time", readyTime, null)).async(true).send();
            subscribeEvent(EVENT_APP_SHUTDOWN, event -> {
                GLOBAL_THREAD_POOL.submit(() -> shutdown(event.context()));
                event.acknowledge();
            });
            // INIT CLEANUP TASK - just for safety
            subscribeEvent(EVENT_APP_HEARTBEAT, this::cleanUps);
            // Initialize config file watching if FileWatcher service is available
            initializeConfigWatching();
            context.newEvent(EVENT_APP_START).broadcast(true).async(true).send();
        } catch (final Exception e) {
            context.error(e, () -> "Failed to start [{}] in [{}]", this.getClass().getSimpleName(), NanoUtils.formatDuration(System.nanoTime() - service_startUpTime));
            shutdown(context);
        }
    }

    /**
     * Returns the root {@link Context}.
     * This context should be only used to manipulate values.
     * {@link Nano#context} should be used for running Lambdas, {@link Service}s, {@link Scheduler}s or send {@link Event}s
     *
     * @return the root {@link Context} associated.
     */
    public Context context() {
        return context;
    }

    /**
     * Creates a {@link Context} specified class.
     *
     * @param clazz The class for which the {@link Context} is to be created.
     * @return A new {@link Context} instance associated with the given class.
     */
    public Context context(final Class<?> clazz) {
        return context.newContext(clazz);
    }

    /**
     * Creates an empty {@link Context} for the specified class.
     *
     * @param clazz The class for which the {@link Context} is to be created.
     * @return A new {@link Context} instance associated with the given class.
     */
    public Context contextEmpty(final Class<?> clazz) {
        return context.newEmptyContext(clazz);
    }

    /**
     * Initiates the shutdown process for the {@link Nano} instance.
     *
     * @param clazz class for which the {@link Context} is to be created.
     * @return The current instance of {@link Nano} for method chaining.
     */
    @Override
    public Nano stop(final Class<?> clazz) {
        return stop(clazz != null ? context(clazz) : null);
    }

    /**
     * Initiates the shutdown process for the {@link Nano} instance.
     *
     * @param context The {@link Context} in which {@link Nano} instance shuts down.
     * @return The current instance of {@link Nano} for method chaining.
     */
    @Override
    public Nano stop(final Context context) {
        (context != null ? context : context(this.getClass())).newEvent(EVENT_APP_SHUTDOWN).broadcast(true).async(true).send();
        return this;
    }

    /**
     * This method blocks the current thread for max 10 seconds until the {@link Nano} instance is no longer ready.
     * This is useful in tests for ensuring that the application has fully stopped before proceeding with further operations.
     *
     * @return The current instance of {@link Nano} for method chaining.
     */
    public Nano waitForStop() {
        NanoUtils.waitForCondition(() -> !isReady(), 10000);
        return this;
    }

    /**
     * Prints the configurations that have been loaded into the {@link Nano} instance.
     */
    public void printParameters() {
        if (context.asBooleanOpt(APP_PARAMS).filter(printCalled -> printCalled).isPresent()) {
            final List<String> secrets = List.of("secret", "token", "pass", "pwd", "bearer", "auth", "private", "ssn");
            final int keyLength = context.keySet().stream().map(String::valueOf).mapToInt(String::length).max().orElse(0);
            context.info(() -> "Configs: " + lineSeparator() + context.entrySet().stream()
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(o1.getKey()), String.valueOf(o2.getKey())))
                .map(config -> String.format("%-" + keyLength + "s  %s", config.getKey(), secrets.stream().anyMatch(s -> String.valueOf(config.getKey()).toLowerCase().contains(s)) ? "****" : config.getValue()))
                .collect(joining(lineSeparator())))
            ;
        }
    }

    /**
     * Sends an event with the specified parameters, either broadcasting it to all listeners or sending it to a targeted listener asynchronously if a response listener is provided.
     *
     * @param event The {@link Event} object that encapsulates the event's context, payload, and payload. use {@link Context#newEvent(Channel, Supplier)} to create an instance.
     * @return The {@link Nano} instance, allowing for method chaining.
     */
    public Nano sendEvent(final Event<?, ?> event) {
        sendEventR(event);
        return this;
    }

    /**
     * Processes an event with the given parameters and decides on the execution path based on the presence of a response listener and the broadcast flag.
     * If a response listener is provided, the event is processed asynchronously; otherwise, it is processed in the current thread. This method creates an {@link Event} instance and triggers the appropriate event handling logic.
     *
     * @param event The {@link Event} object that encapsulates the event's context, payload, and payload. use {@link Context#newEvent(Channel, Supplier)} to create an instance.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public <C, R> Event<C, R> sendEventR(final Event<C, R> event) {
        if (!event.isAsync()) {
            sendEventSameThread(event);
        } else {
            //FIXME: batch processing to avoid too many threads?
            context.run(() -> sendEventSameThread(event));
        }
        return event;
    }

    /**
     * Sends an event on the same thread and determines whether to process it to the first listener.
     * Used {@link Context#newEvent(Channel, Supplier)} from {@link Nano#context(Class)} instead of the core method.
     *
     * @param event The event to be processed.
     * @return self for chaining
     */
    @SuppressWarnings({"java:S2201"})
    public Nano sendEventSameThread(final Event<?, ?> event) {
        if (event.asBooleanOpt("send").orElse(false))
            throw new IllegalStateException("Event already send. Channel [" + event.channel() + "] ack [" + event.acknowledge() + "]", event.error());
        event.put("send", true);
        eventCount.incrementAndGet();
        event.context().tryExecute(() -> {
            boolean match = listeners.getOrDefault(event.channel().id(), Collections.emptySet()).stream().anyMatch(listener -> {
                event.context().tryExecute(() -> listener.accept(event), throwable -> event.context().sendEventError(event, throwable));
                return !event.isBroadcast() && event.isAcknowledged();
            });
            if (!match) {
                match = services.stream().filter(Service::isReady).anyMatch(service -> {
                    event.context().tryExecute(() -> service.receiveEvent(event), throwable -> event.context().sendEventError(event, service, throwable));
                    return !event.isBroadcast() && event.isAcknowledged();
                });
            }
            // LOGGING FALLBACK
            if (event.channel() == EVENT_LOGGING && !match)
                logService.onEvent(event);
        });
        eventCount.decrementAndGet();
        return this;
    }

    /**
     * Shuts down the {@link Nano} instance, ensuring all services and threads are gracefully terminated.
     *
     * @param clazz class for which the {@link Context} is to be created.
     * @return Self for chaining
     */
    protected Nano shutdown(final Class<?> clazz) {
        this.shutdown(context(clazz));
        return this;
    }

    /**
     * Performs the cleanUps operation like memory.
     *
     * @param event the event
     */
    protected void cleanUps(final Event<?, ?> event) {
        // WARN ON HEAP USAGE
        final double usage = heapMemoryUsage();
        final int threshold = context.asIntOpt(CONFIG_OOM_SHUTDOWN_THRESHOLD).orElse(98);
        if (threshold > 0 && usage > (threshold / 100d) && !context.newEvent(EVENT_APP_OOM, () -> usage).send().isAcknowledged()) {
            context.warn(() -> "Out of mana aka memory [{}] threshold [{}] event [{}] shutting down", usage, threshold, EVENT_APP_OOM.name());
            context.put("_app_exit_code", 127);
            shutdown(context);
        }

        // CLEANUP SCHEDULERS
        new HashSet<>(schedulers).stream().filter(scheduler -> scheduler.isShutdown() || scheduler.isTerminated()).forEach(schedulers::remove);
    }

    /**
     * Performs the printActiveProfiles operation.
     */
    protected void printActiveProfiles() {
        final List<String> list = context.asList(String.class, "_scanned_profiles");
        if (!list.isEmpty()) {
            context.debug(() -> "Profiles [{}] Services [{}]",
                list.stream().sorted().collect(joining(", ")),
                services().stream().collect(Collectors.groupingBy(Service::name, Collectors.counting())).entrySet().stream().map(entry -> entry.getValue() > 1 ? "(" + entry.getValue() + ") " + entry.getKey() : entry.getKey()).collect(joining(", "))
            );
        }
    }

    /**
     * Starts and initializes required components.
     *
     * @param startupServices the startupServices
     */
    @SuppressWarnings("SlowListContainsAll")
    protected void startServicesAndLogger(final FunctionOrNull<Context, List<Service>> startupServices) {
        if (startupServices == null || services.stream().noneMatch(LogService.class::isInstance))
            // Use default logger
            this.context.newEvent(EVENT_APP_SERVICE_REGISTER, () -> logService).broadcast(true).send();
        if (startupServices != null) {
            final List<Service> services = startupServices.apply(context);
            if (services != null) {
                // Start services
                context.debug(() -> "Init [{}] services [{}]", services.size(), services.stream().map(Service::name).distinct().collect(joining(", ")));
                context.runAwait(services.toArray(Service[]::new));
                while (!this.services.containsAll(services)) {
                    try {
                        Thread.sleep(16);
                    } catch (final InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Shuts down the {@link Nano} instance, ensuring all services and threads are gracefully terminated.
     *
     * @param context The {@link Context} in which {@link Nano} instance shuts down.
     * @return Self for chaining
     */
    protected Nano shutdown(final Context context) {
        if (isReady.compareAndSet(true, false)) {
            context.info(() -> "Stop {} ...", context.asString(APP_NANO_NAME));
            final int exitCode = context.asIntOpt("_app_exit_code").orElse(0);
            final boolean exitCodeAllowed = context.asBooleanOpt(CONFIG_ENV_PROD).orElse(false);
            gracefulShutdown(context);
            if (exitCodeAllowed)
                exit(context, exitCode);
        }
        return this;
    }

    /**
     * Prints system information for diagnostic purposes. Similar to {@link Nano#toString()}
     *
     * @return Self for chaining
     */
    public Nano printSystemInfo() {
        final long activeThreads = NanoThread.activeCarrierThreads();
        context.debug(() -> "pid [{}] schedulers [{}] services [{}] listeners [{}] cores [{}] usedMemory [{}mb] threadsNano [{}], threadsActive [{}] threadsOther [{}] java [{}] arch [{}] os [{}]",
            pid(),
            schedulers.size(),
            services.size(),
            listeners.values().stream().mapToLong(Collection::size).sum(),
            Runtime.getRuntime().availableProcessors(),
            usedMemoryMB(),
            NanoThread.activeNanoThreads(),
            activeThreads,
            ManagementFactory.getThreadMXBean().getThreadCount() - activeThreads,
            System.getProperty("java.version"),
            System.getProperty("os.arch"),
            System.getProperty("os.name") + " - " + System.getProperty("os.version")
        );
        return this;
    }

    /**
     * Performs the gracefulShutdown operation.
     *
     * @param context the context
     */
    protected void gracefulShutdown(final Context context) {
        try {
            final Thread sequence = new Thread(() -> {
                final long startTimeMs = System.nanoTime();
                // printSystemInfo();  during shutdown, calls like printSystemInfo(), which asks the JVM for live thread details occasionally crashes there. JVM dies mid-shutdown. Can cause "random" failures.
                context.debug(() -> "Shutdown Services count [{}] services [{}]", services.size(), services.stream().map(Service::getClass).map(Class::getSimpleName).distinct().collect(joining(", ")));
                shutdownServices(this.context);
                this.shutdownThreads();
                listeners.clear();
                context.info(() -> "Stopped [{}] in [{}] with uptime [{}]", context.asString(APP_NANO_NAME), NanoUtils.formatDuration(System.nanoTime() - startTimeMs), NanoUtils.formatDuration(System.nanoTime() - createdAtNs));
                schedulers.clear();
                // Interrupt keep-alive thread on shutdown
                if (keepAliveThread.isAlive()) {
                    keepAliveThread.interrupt();
                }
            }, Nano.class.getSimpleName() + " Shutdown-Hook");
            sequence.start();
            sequence.join();
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Performs the exit operation.
     *
     * @param context  the context
     * @param exitCode the exitCode
     */
    protected void exit(final Context context, final int exitCode) {
        try {
            final Thread thread = new Thread(() -> {
                try {
                    Runtime.getRuntime().halt(exitCode);
                } catch (final SecurityException e) {
                    context.error(e, () -> "Failed to set exit code. The dark side is strong.");
                }
            }, Nano.class.getSimpleName() + " Shutdown-Thread");
            thread.start();
            thread.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            context.error(e, () -> "Shutdown was interrupted. The empire strikes back.");
        }
    }

    /**
     * Initialize config file watching if FileWatcher service is available
     */
    private void initializeConfigWatching() {
        // Only set up config watching if FileWatcher service is available
        if (service(FileWatcher.class) != null) {
            // Set up config file watching
            final List<Path> configPaths = CONFIG_FILE_LOCATIONS.stream()
                .map(Path::of)
                .filter(Files::exists)
                .filter(Files::isDirectory)
                .toList();

            if (!configPaths.isEmpty()) {
                final FileWatchRequest configWatchRequest = FileWatchRequest.forFilesWithGroup(configPaths, "CONFIG_CHANGE");
                context.newEvent(EVENT_FILE_WATCH, () -> configWatchRequest).send();
                context.debug(() -> "Initialized config file watching for {} paths", configPaths.size());
            }

            // Subscribe to file changes for config files
            subscribeEvent(EVENT_FILE_CHANGE, this::handleConfigFileChange);
        }
    }

    /**
     * Handle config file changes and trigger EVENT_CONFIG_CHANGE
     */
    private void handleConfigFileChange(final Event<FileChangeEvent, Void> event) {
        event.payloadOpt().filter(fce -> fce.belongsToGroup("CONFIG_CHANGE")).ifPresent(cfe -> {
            context.debug(() -> "Config file changed: [{}] kind [{}]", cfe.path(), cfe.getKindName());
            context.newEvent(EVENT_CONFIG_CHANGE, this::readConfigs).broadcast(true).async(true).send();

        });
    }

    /**
     * Performs the toString operation.
     *
     * @return the result
     */
    @Override
    public String toString() {
        final long activeThreads = NanoThread.activeCarrierThreads();
        return "Nano{" +
            "pid=" + pid() +
            ", schedulers=" + schedulers.size() +
            ", services=" + services.size() +
            ", listeners=" + listeners.values().stream().mapToLong(Collection::size).sum() +
            ", cores=" + Runtime.getRuntime().availableProcessors() +
            ", usedMemory=" + usedMemoryMB() + "mb" +
            ", threadsActive=" + NanoThread.activeNanoThreads() +
            ", threadsNano=" + activeThreads +
            ", threadsOther=" + (ManagementFactory.getThreadMXBean().getThreadCount() - activeThreads) +
            ", java=" + System.getProperty("java.version") +
            ", arch=" + System.getProperty("os.arch") +
            ", os=" + System.getProperty("os.name") + " - " + System.getProperty("os.version") +
            '}';
    }
}
