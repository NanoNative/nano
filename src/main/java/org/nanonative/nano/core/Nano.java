package org.nanonative.nano.core;

import berlin.yuna.typemap.model.FunctionOrNull;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Scheduler;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.helper.logger.logic.NanoLogger;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.nanonative.nano.core.model.Context.APP_PARAMS;
import static org.nanonative.nano.core.model.Context.CONFIG_ENV_PROD;
import static org.nanonative.nano.core.model.Context.CONFIG_OOM_SHUTDOWN_THRESHOLD;
import static org.nanonative.nano.core.model.Context.CONTEXT_CLASS_KEY;
import static org.nanonative.nano.core.model.Context.CONTEXT_NANO_KEY;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_OOM;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SHUTDOWN;
import static org.nanonative.nano.core.model.Context.EVENT_APP_START;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.NanoUtils.generateNanoName;
import static org.nanonative.nano.helper.event.EventChannelRegister.eventNameOf;
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
        final long initTime = System.currentTimeMillis() - createdAtMs;
        logger.trace(() -> "Init [{}] in [{}]", this.getClass().getSimpleName(), NanoUtils.formatDuration(initTime));
        printParameters();

        final long service_startUpTime = System.currentTimeMillis();
        logger.debug(() -> "Start {}", this.getClass().getSimpleName());
        // INIT SHUTDOWN HOOK
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(context(this.getClass()))));
        startServices(startupServices);
        run(() -> context, () -> sendEvent(EVENT_APP_HEARTBEAT, context, this, result -> {}, true), 256, 256, MILLISECONDS, () -> false);
        run(() -> context, System::gc, 5, 5, SECONDS, () -> false);
        final long readyTime = System.currentTimeMillis() - service_startUpTime;
        printActiveProfiles();
        logger.info(() -> "Started [{}] in [{}]", generateNanoName("%s%.0s%.0s%.0s"), NanoUtils.formatDuration(readyTime));
        printSystemInfo();
        sendEvent(EVENT_METRIC_UPDATE, context, new MetricUpdate(GAUGE, "application.started.time", initTime, null), result -> {}, false);
        sendEvent(EVENT_METRIC_UPDATE, context, new MetricUpdate(GAUGE, "application.ready.time", readyTime, null), result -> {}, false);
        subscribeEvent(EVENT_APP_SHUTDOWN, event -> event.acknowledge(() -> CompletableFuture.runAsync(() -> shutdown(event.context()))));
        // INIT CLEANUP TASK - just for safety
        subscribeEvent(EVENT_APP_HEARTBEAT, this::cleanUps);
        sendEvent(EVENT_APP_START, context, this, result -> {}, true);
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
     * Creates a {@link Context} with {@link NanoLogger} for the specified class.
     *
     * @param clazz The class for which the {@link Context} is to be created.
     * @return A new {@link Context} instance associated with the given class.
     */
    public Context context(final Class<?> clazz) {
        return context.newContext(clazz);
    }

    /**
     * Creates an empty {@link Context} with {@link NanoLogger} for the specified class.
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
        sendEvent(EVENT_APP_SHUTDOWN, context != null ? context : context(this.getClass()), null, result -> {
        }, true);
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
            logger.info(() -> "Configs: " + lineSeparator() + context.entrySet().stream().sorted().map(config -> String.format("%-" + keyLength + "s  %s", config.getKey(), secrets.stream().anyMatch(s -> String.valueOf(config.getKey()).toLowerCase().contains(s)) ? "****" : config.getValue())).collect(joining(lineSeparator())));
        }
    }

    /**
     * Sends an event with the specified parameters, either broadcasting it to all listeners or sending it to a targeted listener asynchronously if a response listener is provided.
     *
     * @param channelId        The integer representing the channelId of the event. This typically corresponds to a specific action or state change.
     * @param context          The {@link Context} in which the event is being sent, providing environmental data and configurations.
     * @param payload          The data or object associated with this event. This could be any relevant information that needs to be communicated through the event.
     * @param responseListener A consumer that handles the response of the event processing. If null, the event is processed in the same thread; otherwise, it's processed asynchronously.
     * @param broadcast        A boolean flag indicating whether the event should be broadcast to all listeners. If true, the event is broadcast; if false, it is sent to a targeted listener.
     * @return The {@link Nano} instance, allowing for method chaining.
     */
    public Nano sendEvent(final int channelId, final Context context, final Object payload, final Consumer<Object> responseListener, final boolean broadcast) {
        sendEventReturn(channelId, context, payload, responseListener, broadcast);
        return this;
    }

    /**
     * Processes an event with the given parameters and decides on the execution path based on the presence of a response listener and the broadcast flag.
     * If a response listener is provided, the event is processed asynchronously; otherwise, it is processed in the current thread. This method creates an {@link Event} instance and triggers the appropriate event handling logic.
     *
     * @param channelId        The integer representing the channelId of the event, identifying the nature or action of the event.
     * @param context          The {@link Context} associated with the event, encapsulating environment and configuration details.
     * @param payload          The payload of the event, containing data relevant to the event's context and purpose.
     * @param responseListener A consumer for handling the event's response. If provided, the event is handled asynchronously; if null, the handling is synchronous.
     * @param broadCast        Determines the event's distribution: if true, the event is made available to all listeners; if false, it targets specific listeners based on the implementation logic.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public Event sendEventReturn(final int channelId, final Context context, final Object payload, final Consumer<Object> responseListener, final boolean broadCast) {
        final Event event = new Event(channelId, context, channelId == EVENT_CONFIG_CHANGE && payload instanceof final Map<?, ?> map ? new TypeMap(map) : payload, responseListener);
        if (responseListener == null) {
            sendEventSameThread(event, broadCast);
        } else {
            //FIXME: batch processing to avoid too many threads?
            context.run(() -> sendEventSameThread(event, broadCast));
        }
        return event;
    }

    /**
     * Sends an event on the same thread and determines whether to process it to the first listener.
     * Used {@link Context#sendEvent(int, Object)} from {@link Nano#context(Class)} instead of the core method.
     *
     * @param event     The event to be processed.
     * @param broadcast Whether to send the event only to the first matching listener or to all.
     * @return self for chaining
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Nano sendEventSameThread(final Event event, final boolean broadcast) {
        eventCount.incrementAndGet();
        event.context().tryExecute(() -> {
            final boolean match = listeners.getOrDefault(event.channelId(), Collections.emptySet()).stream().anyMatch(listener -> {
                event.context().tryExecute(() -> listener.accept(event), throwable -> event.context().sendEventError(event, throwable));
                return !broadcast && event.isAcknowledged();
            });
            if (!match) {
                services.stream().filter(Service::isReady).anyMatch(service -> {
                    event.context().tryExecute(() -> service.onEvent(event), throwable -> event.context().sendEventError(event, service, throwable));
                    return !broadcast && event.isAcknowledged();
                });
            }
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

    protected void cleanUps(final Event event) {
        // WARN ON HEAP USAGE
        final double usage = heapMemoryUsage();
        final int threshold = context.asIntOpt(CONFIG_OOM_SHUTDOWN_THRESHOLD).orElse(98);
        if (threshold > 0 && usage > (threshold / 100d) && !context.sendEventReturn(EVENT_APP_OOM, usage).isAcknowledged()) {
            logger.warn(() -> "Out of mana aka memory [{}] threshold [{}] event [{}] shutting down", usage, threshold, eventNameOf(EVENT_APP_OOM));
            context.put("_app_exit_code", 127);
            shutdown(context);
        }

        // CLEANUP SCHEDULERS
        new HashSet<>(schedulers).stream().filter(scheduler -> scheduler.isShutdown() || scheduler.isTerminated()).forEach(schedulers::remove);
    }

    protected void printActiveProfiles() {
        final List<String> list = context.asList(String.class, "_scanned_profiles");
        if (!list.isEmpty()) {
            logger.debug(() -> "Profiles [{}] Services [{}]",
                list.stream().sorted().collect(joining(", ")),
                services().stream().collect(Collectors.groupingBy(Service::name, Collectors.counting())).entrySet().stream().map(entry -> entry.getValue() > 1 ? "(" + entry.getValue() + ") " + entry.getKey() : entry.getKey()).collect(joining(", "))
            );
        }
    }

    protected void startServices(final FunctionOrNull<Context, List<Service>> startupServices) {
        if (startupServices != null) {
            final List<Service> services = startupServices.apply(context);
            if (services != null) {
                logger.debug(() -> "StartupServices [{}] services [{}]", services.size(), services.stream().map(Service::name).distinct().collect(joining(", ")));
                context.runAwait(services.toArray(Service[]::new));
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
            final int exitCode = context.asIntOpt("_app_exit_code").orElse(0);
            final boolean exitCodeAllowed = context.asBooleanOpt(CONFIG_ENV_PROD).orElse(false);
            gracefulShutdown(context.logger());
            if (exitCodeAllowed)
                exit(context.logger(), exitCode);
        }
        return this;
    }

    public String hostname() {
        return String.valueOf(context.computeIfAbsent("app_hostname", value -> {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (final UnknownHostException e) {
                return "Localhost";
            }
        }));
    }

    /**
     * Prints system information for diagnostic purposes. Similar to {@link Nano#toString()}
     *
     * @return Self for chaining
     */
    public Nano printSystemInfo() {
        final long activeThreads = NanoThread.activeCarrierThreads();
        logger.debug(() -> "pid [{}] schedulers [{}] services [{}] listeners [{}] cores [{}] usedMemory [{}mb] threadsNano [{}], threadsActive [{}] threadsOther [{}] java [{}] arch [{}] os [{}] host [{}]",
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
            System.getProperty("os.name") + " - " + System.getProperty("os.version"),
            hostname()
        );
        return this;
    }

    protected void gracefulShutdown(final NanoLogger logger) {
        try {
            final Thread sequence = new Thread(() -> {
                final long startTimeMs = System.currentTimeMillis();
                logger.info(() -> "Stop {} ...", this.getClass().getSimpleName());
                printSystemInfo();
                logger.debug(() -> "Shutdown Services count [{}] services [{}]", services.size(), services.stream().map(Service::getClass).map(Class::getSimpleName).distinct().collect(joining(", ")));
                shutdownServices(this.context);
                this.shutdownThreads();
                listeners.clear();
                printSystemInfo();
                logger.info(() -> "Stopped [{}] in [{}] with uptime [{}]", generateNanoName("%s%.0s%.0s%.0s"), NanoUtils.formatDuration(System.currentTimeMillis() - startTimeMs), NanoUtils.formatDuration(System.currentTimeMillis() - createdAtMs));
                schedulers.clear();
            }, Nano.class.getSimpleName() + " Shutdown-Hook");
            sequence.start();
            sequence.join();
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    protected void exit(final NanoLogger logger, final int exitCode) {
        try {
            final Thread thread = new Thread(() -> {
                try {
                    Runtime.getRuntime().halt(exitCode);
                } catch (final SecurityException e) {
                    logger.error(e, () -> "Failed to set exit code. The dark side is strong.");
                }
            }, Nano.class.getSimpleName() + " Shutdown-Thread");
            thread.start();
            thread.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error(e, () -> "Shutdown was interrupted. The empire strikes back.");
        }
    }

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
            ", host=" + hostname() +
            '}';
    }
}
