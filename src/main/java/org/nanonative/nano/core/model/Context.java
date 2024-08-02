package org.nanonative.nano.core.model;

import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.NanoServices;
import org.nanonative.nano.helper.ExRunnable;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.helper.logger.LogFormatRegister;
import org.nanonative.nano.helper.logger.logic.NanoLogger;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpMethod;
import berlin.yuna.typemap.model.ConcurrentTypeMap;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.NanoThreads;
import org.nanonative.nano.helper.config.ConfigRegister;
import org.nanonative.nano.helper.event.EventChannelRegister;
import org.nanonative.nano.helper.logger.logic.LogQueue;
import org.nanonative.nano.helper.logger.model.LogLevel;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.stream.Stream;

import static org.nanonative.nano.core.model.Service.threadsOf;
import static berlin.yuna.typemap.config.TypeConversionRegister.registerTypeConvert;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"unused", "UnusedReturnValue", "java:S2160"})
public class Context extends ConcurrentTypeMap {

    // Context keys
    public static final String CONTEXT_TRACE_ID_KEY = "app_core_context_trace_id";
    public static final String CONTEXT_LOGGER_KEY = "app_core_context_logger";
    public static final String CONTEXT_PARENT_KEY = "app_core_context_parent";
    public static final String CONTEXT_CLASS_KEY = "app_core_context_class";
    public static final String CONTEXT_NANO_KEY = "app_core_context_nano";
    public static final String CONTEXT_LOG_QUEUE_KEY = "app_core_context_log_queue";

    // Register configurations
    public static final String APP_HELP = ConfigRegister.registerConfig("help", "Lists available config keys");
    public static final String APP_PARAMS = ConfigRegister.registerConfig("app_params_print", "Pints all config values");
    public static final String CONFIG_PROFILES = ConfigRegister.registerConfig("app_profiles", "Active config profiles for the application");
    public static final String CONFIG_LOG_LEVEL = ConfigRegister.registerConfig("app_log_level", "Log level for the application (see " + LogLevel.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_FORMATTER = ConfigRegister.registerConfig("app_log_formatter", "Log formatter (see " + LogFormatRegister.class.getSimpleName() + ")");
    public static final String CONFIG_LOG_QUEUE_SIZE = ConfigRegister.registerConfig("app_log_queue_size", "Log queue size. A full queue means that log messages will start to wait to be executed (see " + LogQueue.class.getSimpleName() + ")");
    public static final String CONFIG_THREAD_POOL_TIMEOUT_MS = ConfigRegister.registerConfig("app_thread_pool_shutdown_timeout_ms", "Timeout for thread pool shutdown in milliseconds (see " + NanoThreads.class.getSimpleName() + ")");
    public static final String CONFIG_PARALLEL_SHUTDOWN = ConfigRegister.registerConfig("app_service_shutdown_parallel", "Enable or disable parallel service shutdown (see " + NanoServices.class.getSimpleName() + "). Enabled = Can increase the shutdown performance on`true`");
    public static final String CONFIG_OOM_SHUTDOWN_THRESHOLD = ConfigRegister.registerConfig("app_oom_shutdown_threshold", "Sets the threshold for heap in percentage to send an `EVENT_APP_OOM`. default = `98`, disabled = `-1`. If the event is unhandled, tha pp will try to shutdown with last resources");
    public static final String CONFIG_ENV_PROD = ConfigRegister.registerConfig("app_env_prod", "Enable or disable behaviour e.g. exit codes. This is useful in prod environments specially on error cases. default = `false`");

    // Register event channels
    public static final int EVENT_APP_START = EventChannelRegister.registerChannelId("APP_START");
    public static final int EVENT_APP_SHUTDOWN = EventChannelRegister.registerChannelId("APP_SHUTDOWN");
    public static final int EVENT_APP_SERVICE_REGISTER = EventChannelRegister.registerChannelId("APP_SERVICE_REGISTER");
    public static final int EVENT_APP_SERVICE_UNREGISTER = EventChannelRegister.registerChannelId("APP_SERVICE_UNREGISTER");
    public static final int EVENT_APP_SCHEDULER_REGISTER = EventChannelRegister.registerChannelId("APP_SCHEDULER_REGISTER");
    public static final int EVENT_APP_SCHEDULER_UNREGISTER = EventChannelRegister.registerChannelId("APP_SCHEDULER_UNREGISTER");
    public static final int EVENT_APP_UNHANDLED = EventChannelRegister.registerChannelId("EVENT_APP_UNHANDLED");
    public static final int EVENT_APP_ERROR = EventChannelRegister.registerChannelId("EVENT_APP_ERROR");
    public static final int EVENT_APP_OOM = EventChannelRegister.registerChannelId("EVENT_APP_OOM");
    public static final int EVENT_APP_HEARTBEAT = EventChannelRegister.registerChannelId("EVENT_HEARTBEAT");
    public static final int EVENT_CONFIG_CHANGE = EventChannelRegister.registerChannelId("EVENT_CONFIG_CHANGE");

    static {
        // Register type converters
        registerTypeConvert(String.class, Formatter.class, LogFormatRegister::getLogFormatter);
        registerTypeConvert(String.class, LogLevel.class, LogLevel::nanoLogLevelOf);
        registerTypeConvert(LogLevel.class, String.class, Enum::name);
        registerTypeConvert(ContentType.class, String.class, ContentType::name);
        registerTypeConvert(String.class, ContentType.class, ContentType::fromValue);
        registerTypeConvert(HttpMethod.class, String.class, HttpMethod::name);
        registerTypeConvert(String.class, HttpMethod.class, HttpMethod::valueOf);
        registerTypeConvert(String.class, java.net.http.HttpClient.Version.class, string -> {
            if ("1".equals(string) || HTTP_1_1.toString().equals(string)) {
                return HTTP_1_1;
            } else if ("2".equals(string) || HTTP_2.toString().equals(string)) {
                return HTTP_2;
            }
            return null;
        });
    }

    // fast and lazy loaded accessor
    protected transient Nano nano;

    /**
     * Creates a new root context with a unique trace ID.
     *
     * @return The newly created root context.
     */
    public static Context createRootContext(final Class<?> clazz) {
        return new Context(clazz);
    }

    /**
     * Retrieves the {@link Nano} instance associated with this context.
     *
     * @return The {@link Nano} instance associated with this context.
     */
    public Nano nano() {
        if (nano == null) {
            nano = get(Nano.class, CONTEXT_NANO_KEY);
        }
        return nano;
    }

    /**
     * Retrieves the {@link Context} parent associated with this context.
     *
     * @return Parent {@link Context} or null
     */
    public Context parent() {
        return this.get(Context.class, CONTEXT_PARENT_KEY);
    }

    /**
     * Retrieves the last created trace ID of the context.
     *
     * @return The last created trace ID of the context.
     */
    public String traceId() {
        return get(String.class, CONTEXT_TRACE_ID_KEY);
    }

    /**
     * Retrieves the trace ID at the specified index.
     *
     * @param index The index of the trace ID to retrieve.
     * @return The trace ID at the specified index, or the last trace ID if the index is out of bounds.
     */
    public String traceId(final int index) {
        return index < 1 ? traceId() : Stream.iterate(Optional.of(this), opt -> opt.flatMap(ctx -> Optional.ofNullable(ctx.parent())))
            .limit(index + 1L)
            .reduce((first, second) -> second)
            .flatMap(ctx -> ctx.map(Context::traceId))
            .orElse(traceId());
    }

    /**
     * Retrieves all trace IDs associated with this context.
     *
     * @return A list of all trace IDs associated with this context.
     */
    public List<String> traceIds() {
        return Stream.iterate(Optional.of(this), Optional::isPresent, opt -> opt.flatMap(ctx -> Optional.ofNullable(ctx.parent())))
            .map(opt -> opt.flatMap(ctx -> Optional.ofNullable(ctx.traceId())))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Retrieves the logger associated with this context.
     *
     * @return The logger associated with this context.
     */
    public NanoLogger logger() {
        final NanoLogger logger = get(NanoLogger.class, CONTEXT_LOGGER_KEY);
        return logger != null ? logger : initLogger();
    }

    /**
     * Creates new Context with a new logger and trace ID.
     *
     * @param clazz The class to use for the logger name. If null, the logger name will be the class of the context.
     * @return The newly created context.
     */
    public Context newContext(final Class<?> clazz) {
        return new Context(this, clazz, false);
    }

    /**
     * Creates new empty Context with a new logger and trace ID.
     *
     * @param clazz The class to use for the logger name. If null, the logger name will be the class of the context.
     * @return The newly created context.
     */
    public Context newEmptyContext(final Class<?> clazz) {
        return new Context(this, clazz, true);
    }

    /**
     * Retrieves the log level of the context logger.
     *
     * @return The log level of the context logger.
     */
    public LogLevel logLevel() {
        return logger().level();
    }

    //########## CHAINING HELPERS ##########

    @Override
    public void putAll(final Map<?, ?> map) {
        super.putAll(map);
        // Auto change logger
        getOpt(NanoLogger.class, CONTEXT_LOGGER_KEY).ifPresent(logger -> logger.configure(map instanceof final TypeMap typeMap ? typeMap : new TypeMap(map)));
    }

    /**
     * Puts a key-value pair into the context.
     *
     * @param key   The key to put into the context. Null keys are interpreted as empty strings.
     * @param value The value to associate with the key.
     * @return The current {@link Context} instance, allowing for method chaining and further configuration.
     */
    @Override
    public Context put(final Object key, final Object value) {
        // ConcurrentHashMap does not allow null keys or values.
        super.put(key, value != null ? value : "");
        // Auto change logger
        getOpt(NanoLogger.class, CONTEXT_LOGGER_KEY).ifPresent(logger -> logger.configure(new TypeMap().putReturn(key, value)));
        return this;
    }

    /**
     * Associates the specified value with the specified key in this map.
     *
     * @param key   the key with which the specified value is to be associated.
     * @param value the value to be associated with the specified key.
     * @return the updated {@link ConcurrentTypeMap} instance for chaining.
     */
    @Override
    public Context putReturn(final Object key, final Object value) {
        // ConcurrentHashMap does not allow null keys or values.
        this.put(key, value);
        return this;
    }

    /**
     * Registers an event listener for a specific event type.
     *
     * @param channelId The integer identifier of the event type.
     * @param listener  The consumer function that processes the {@link Event}.
     * @return Self for chaining
     */
    public Context subscribeEvent(final int channelId, final Consumer<Event> listener) {
        nano().subscribeEvent(channelId, listener);
        return this;
    }

    /**
     * Removes a registered event listener for a specific event type.
     *
     * @param channelId The integer identifier of the event type.
     * @param listener  The consumer function to be removed.
     * @return Self for chaining
     */
    public Context unsubscribeEvent(final int channelId, final Consumer<Event> listener) {
        nano().unsubscribeEvent(channelId, listener);
        return this;
    }

    /**
     * Executes a task asynchronously after a specified delay.
     *
     * @param task     The task to execute.
     * @param delay    The delay before executing the task.
     * @param timeUnit The time unit of the delay parameter.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final long delay, final TimeUnit timeUnit) {
        nano().run(() -> this, task, delay, timeUnit);
        return this;
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     *
     * @param task   The task to execute.
     * @param delay  The initial delay before executing the task.
     * @param period The period between successive task executions.
     * @param unit   The time unit of the initialDelay and period parameters.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final long delay, final long period, final TimeUnit unit) {
        return run(task, delay, period, unit, () -> false);
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     *
     * @param task   The task to execute.
     * @param delay  The initial delay before executing the task.
     * @param period The period between successive task executions.
     * @param unit   The time unit of the initialDelay and period parameters.
     * @param until  A BooleanSupplier indicating the termination condition. <code>true</code> stops the next execution.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final long delay, final long period, final TimeUnit unit, final BooleanSupplier until) {
        nano().run(() -> this, task, delay, period, unit, until);
        return this;
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final LocalTime atTime) {
        nano().run(() -> this, task, atTime, () -> false);
        return this;
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @param until  A BooleanSupplier indicating the termination condition. <code>true</code> stops the next execution.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final LocalTime atTime, final BooleanSupplier until) {
        nano().run(() -> this, task, atTime, until);
        return this;
    }

    //########## LOGGING HELPERS ##########

    /**
     * Sets the logger name for the context logger.
     *
     * @return The created {@link NanoLogger}
     */
    protected NanoLogger initLogger() {
        final NanoLogger logger = new NanoLogger(clazz());
        ofNullable(parent()).ifPresentOrElse(p -> logger
                .level(p.logger().level())
                .logQueue(p.logger().logQueue())
                .formatter(p.logger().formatter()),
            () -> logger
                .level(getOpt(LogLevel.class, CONFIG_LOG_LEVEL).orElse(LogLevel.INFO))
                .formatter(getOpt(Formatter.class, CONFIG_LOG_FORMATTER).orElseGet(() -> LogFormatRegister.getLogFormatter("console"))));
        put(CONTEXT_LOGGER_KEY, logger);
        return logger;
    }

    //########## ASYNC HELPERS ##########

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param runnable function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context run(final ExRunnable... runnable) {
        runReturn(runnable);
        return this;
    }

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context runHandled(final Consumer<Unhandled> onFailure, final ExRunnable... runnable) {
        runReturnHandled(onFailure, runnable);
        return this;
    }

    /**
     * Executes one or multiple {@link Service} asynchronously.
     *
     * @param services The {@link Service} to be appended.
     * @return The {@link Context} object for chaining further operations.
     */
    public Context run(final Service... services) {
        runReturn(services);
        return this;
    }

    //########## ASYNC RETURN HELPER ##########

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param runnable function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runReturn(final ExRunnable... runnable) {
        return stream(runnable).map(task -> new NanoThread(this).run(
            this.nano() == null ? null : nano().threadPool(),
            () -> this.nano() == null ? null : nano().contextEmpty(clazz()),
            task
        )).toArray(NanoThread[]::new);
    }

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runReturnHandled(final Consumer<Unhandled> onFailure, final ExRunnable... runnable) {
        return stream(runnable).map(task -> new NanoThread(this)
            .onComplete((thread, error) -> {
                if (error != null)
                    onFailure.accept(new Unhandled(this, thread, error));
            }).run(
                nano() == null ? null : nano().threadPool(),
                () -> this.nano() == null ? null : nano().contextEmpty(clazz()),
                task
            )
        ).toArray(NanoThread[]::new);
    }

    /**
     * Executes one or multiple {@link Service} asynchronously.
     *
     * @param services The {@link Service} to be appended.
     * @return {@link NanoThread}s
     */
    public NanoThread[] runReturn(final Service... services) {
        try {
            return threadsOf(this, services);
        } catch (final Exception exception) {
            sendEventError(services.length == 1 ? services[0] : services, exception);
            Thread.currentThread().interrupt();
            return new NanoThread[0];
        }
    }

    //########## ASYNC AWAIT HELPER ##########

    /**
     * Executes asynchronously and waits for all runnable to be ready
     *
     * @param runnable function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context runAwait(final ExRunnable... runnable) {
        NanoThread.waitFor(runReturn(runnable));
        return this;
    }

    /**
     * Executes asynchronously and waits for all runnable to be ready
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context runAwaitHandled(final Consumer<Unhandled> onFailure, final ExRunnable... runnable) {
        NanoThread.waitFor(runReturnHandled(onFailure, runnable));
        return this;
    }

    /**
     * Executes asynchronously and waits for all {@link Service} to be ready
     *
     * @return The {@link Context} object for chaining further operations.
     */
    public Context runAwait(final Service... services) {
        runAwaitReturn(services);
        return this;
    }

    //########## ASYNC AWAIT HELPER RETURN ##########

    /**
     * Executes asynchronously and waits for all {@link Service} to be ready
     *
     * @param runnable function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runAwaitReturn(final ExRunnable... runnable) {
        return NanoThread.waitFor(runReturn(runnable));
    }

    /**
     * Executes and waits for all {@link Service} to be ready
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runAwaitReturnHandled(final Consumer<Unhandled> onFailure, final ExRunnable... runnable) {
        return NanoThread.waitFor(runReturnHandled(onFailure, runnable));
    }

    /**
     * Executes and waits for all {@link Service} to be ready
     *
     * @return {@link NanoThread}s
     */
    public NanoThread[] runAwaitReturn(final Service... services) {
        return NanoThread.waitFor(runReturn(services));
    }

    //########## EVENT HELPER ##########

    /**
     * Sends an unhandled event with the provided, nullable payload and exception. If the event is not acknowledged, the error message is logged.
     *
     * @param payload   The payload of the unhandled event, containing data relevant to the event's context and purpose.
     * @param throwable The exception that occurred during the event processing.
     * @return self for chaining
     */
    public Context sendEventError(final Object payload, final Throwable throwable) {
        // prevent loops
        final Event event = payload instanceof final Event evt ? evt : new Event(EVENT_APP_ERROR, this, payload, null);
        if (event.channelId() != EVENT_APP_UNHANDLED) {
            nano().sendEventSameThread(event.cache(Event.EVENT_ORIGINAL_CHANNEL_ID, event.channelId()).channelId(EVENT_APP_UNHANDLED).error(throwable), false);
            if (!event.isAcknowledged())
                logger().error(throwable, () -> "Event [{}] went rogue.", event.nameOrg());
        } else {
            logger().error(throwable, () -> "Event [{}] went rogue.", event.nameOrg());
        }
        return this;
    }

    /**
     * Sends an unhandled event with the provided, nullable payload and exception. If the event is not acknowledged, the error message is logged.
     *
     * @param event     The unhandled event, containing data relevant to the event's context and purpose.
     * @param service   The service which failed to handle the event.
     * @param throwable The exception that occurred during the event processing.
     * @return self for chaining
     */
    public Context sendEventError(final Event event, final Service service, final Throwable throwable) {
        // loop prevention
        if (event.channelId() == EVENT_APP_UNHANDLED)
            event.context().logger().error(throwable, () -> "Unhandled event [{}] service [{}]", event.nameOrg(), service.name());
        if (service.onFailure(event.error(throwable)) == null) {
            event.context().sendEventError(event, throwable);
        }
        return this;
    }

    /**
     * Sends an event of the specified type with the provided payload within this context without expecting a response.
     * This method is used for sending targeted events that do not require asynchronous processing or response handling.
     *
     * @param channelId The integer representing the type of the event, identifying the nature or action of the event.
     * @param payload   The payload of the event, containing data relevant to the event's context and purpose.
     * @return The current {@link Context} instance, allowing for method chaining and further configuration.
     */
    public Context sendEvent(final int channelId, final Object payload) {
        nano().sendEvent(channelId, this, payload, null, false);
        return this;
    }

    /**
     * Sends an event of the specified type with the provided payload within this context, expecting a response that is handled by the provided responseListener.
     * This method allows for asynchronous event processing and response handling through the specified consumer.
     *
     * @param channelId        The integer representing the type of the event.
     * @param payload          The payload of the event, containing the data to be communicated.
     * @param responseListener A consumer that processes the response of the event. This allows for asynchronous event handling and response processing.
     * @return The current {@link Context} instance, facilitating method chaining and further actions.
     */
    public Context sendEvent(final int channelId, final Object payload, final Consumer<Object> responseListener) {
        nano().sendEvent(channelId, this, payload, responseListener, false);
        return this;
    }

    /**
     * Broadcasts an event of the specified type with the provided payload to all listeners within this context without expecting a response.
     * This method is ideal for notifying all interested parties of a particular event where no direct response is required.
     *
     * @param channelId The integer representing the type of the event, used to notify all listeners interested in this type of event.
     * @param payload   The payload of the event, containing information relevant to the broadcast.
     * @return The current {@link Context} instance, enabling method chaining and additional configurations.
     */
    public Context broadcastEvent(final int channelId, final Object payload) {
        broadcastEvent(channelId, payload, null);
        return this;
    }

    /**
     * Broadcasts an event of the specified type with the provided payload to all listeners within this context, expecting a response that is handled by the provided responseListener.
     * This method allows for the broad dissemination of an event while also facilitating asynchronous response processing.
     *
     * @param channelId        The integer representing the type of the event.
     * @param payload          The payload associated with the event, intended for widespread distribution.
     * @param responseListener A consumer that handles the response of the event, enabling asynchronous processing and response handling across multiple listeners.
     * @return The current {@link Context} instance, allowing for method chaining and further actions.
     */
    public Context broadcastEvent(final int channelId, final Object payload, final Consumer<Object> responseListener) {
        nano().sendEvent(channelId, this, payload, responseListener, true);
        return this;
    }

    //########## EVENT RETURN HELPER ##########

    /**
     * Sends an event of the specified type with the provided payload within this context without expecting a response.
     * This method is used for sending targeted events that do not require asynchronous processing or response handling.
     *
     * @param channelId The integer representing the type of the event, identifying the nature or action of the event.
     * @param payload   The payload of the event, containing data relevant to the event's context and purpose.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public Event sendEventReturn(final int channelId, final Object payload) {
        return sendEventReturn(channelId, payload, null);
    }

    /**
     * Sends an event of the specified type with the provided payload within this context, expecting a response that is handled by the provided responseListener.
     * This method allows for asynchronous event processing and response handling through the specified consumer.
     *
     * @param channelId        The integer representing the type of the event.
     * @param payload          The payload of the event, containing the data to be communicated.
     * @param responseListener A consumer that processes the response of the event. This allows for asynchronous event handling and response processing.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public Event sendEventReturn(final int channelId, final Object payload, final Consumer<Object> responseListener) {
        return nano().sendEventReturn(channelId, this, payload, responseListener, false);
    }

    /**
     * Broadcasts an event of the specified type with the provided payload to all listeners within this context without expecting a response.
     * This method is ideal for notifying all interested parties of a particular event where no direct response is required.
     *
     * @param channelId The integer representing the type of the event, used to notify all listeners interested in this type of event.
     * @param payload   The payload of the event, containing information relevant to the broadcast.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public Event broadcastEventReturn(final int channelId, final Object payload) {
        return broadcastEventReturn(channelId, payload, null);
    }

    /**
     * Broadcasts an event of the specified type with the provided payload to all listeners within this context, expecting a response that is handled by the provided responseListener.
     * This method allows for the broad dissemination of an event while also facilitating asynchronous response processing.
     *
     * @param channelId        The integer representing the type of the event.
     * @param payload          The payload associated with the event, intended for widespread distribution.
     * @param responseListener A consumer that handles the response of the event, enabling asynchronous processing and response handling across multiple listeners.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public Event broadcastEventReturn(final int channelId, final Object payload, final Consumer<Object> responseListener) {
        return nano().sendEventReturn(channelId, this, payload, responseListener, true);
    }

    /**
     * Registers a new event type with a given name if it does not already exist.
     * If the event type already exists, it returns the existing event type's ID.
     *
     * @param channelName The name of the event type to register.
     * @return The ID of the newly registered event type, or the ID of the existing event type
     * if it already exists. Returns -1 if the input is null or empty.
     */
    public int registerChannelId(final String channelName) {
        return EventChannelRegister.registerChannelId(channelName);
    }

    /**
     * Retrieves the name of an event type given its ID.
     *
     * @param channelId The ID of the event type.
     * @return The name of the event type associated with the given ID, or null if not found.
     */
    public String eventNameOf(final int channelId) {
        return EventChannelRegister.eventNameOf(channelId);
    }

    /**
     * Attempts to find the ID of an event type based on its name.
     * This method is primarily used for debugging purposes or startup and is not optimized for performance.
     *
     * @param channelName The name of the event type.
     * @return An {@link Optional} containing the ID of the event type if found, or empty if not found
     * or if the input is null or empty.
     */
    public Optional<Integer> channelIdOf(final String channelName) {
        return EventChannelRegister.eventIdOf(channelName);
    }

    /**
     * Retrieves a {@link Service} of a specified type.
     *
     * @param <S>          The type of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the {@link Service} to retrieve.
     * @return The first instance of the specified {@link Service}, or null if not found.
     */
    public <S extends Service> S service(final Class<S> serviceClass) {
        return nano().service(serviceClass);
    }

    /**
     * Waits for a {@link Service} of a specified type to be available within the given timeout.
     *
     * @param <S>          The type of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the {@link Service} to retrieve.
     * @param timeoutMs    The maximum time to wait, in milliseconds.
     * @return The first instance of the specified {@link Service}, or null if not found within the timeout.
     */
    public <S extends Service> S service(final Class<S> serviceClass, final long timeoutMs) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            final S service = service(serviceClass);
            if (service != null)
                return service;
            try {
                Thread.sleep(16);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * Retrieves a list of services of a specified type.
     *
     * @param <S>          The type of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the service to retrieve.
     * @return A list of services of the specified type. If no services of this type are found,
     * an empty list is returned.
     */
    public <S extends Service> List<S> services(final Class<S> serviceClass) {
        return nano().services(serviceClass);
    }

    /**
     * Provides an unmodifiable list of all registered {@link Service}.
     *
     * @return An unmodifiable list of {@link Service} instances.
     */
    public List<Service> services() {
        return nano().services();
    }

    protected Context(final Class<?> clazz) {
        this(null, clazz, false);
    }

    protected Context(final Context parent, final Class<?> clazz) {
        this(parent, clazz, false);
    }

    @SuppressWarnings("java:S3358")
    protected Context(final Context parent, final Class<?> clazz, final boolean empty) {
        super(empty ? null : parent);
        final Class<?> resolvedClass = clazz != null ? clazz : (parent == null ? Context.class : parent.clazz());
        this.put(CONTEXT_NANO_KEY, parent != null ? parent.get(Nano.class, CONTEXT_NANO_KEY) : null);
        this.put(CONTEXT_CLASS_KEY, resolvedClass);
        this.put(CONTEXT_TRACE_ID_KEY, (resolvedClass.getSimpleName()) + "/" + UUID.randomUUID().toString().replace("-", ""));
        if (parent != null)
            this.put(CONTEXT_PARENT_KEY, parent);
    }

    private Class<?> clazz() {
        return this.getOpt(Class.class, CONTEXT_CLASS_KEY).orElse(Context.class);
    }

    public void tryExecute(final ExRunnable operation) {
        tryExecute(operation, null);
    }

    public void tryExecute(final ExRunnable operation, final Consumer<Throwable> consumer) {
        NanoUtils.tryExecute(() -> this, operation, consumer);
    }

    @Override
    public String toString() {
        return "Context{" +
            "size=" + size() +
            ", loglevel=" + getOpt(NanoLogger.class, CONTEXT_LOGGER_KEY).map(NanoLogger::level).orElse(null) +
            ", logQueue=" + getOpt(NanoLogger.class, CONTEXT_LOGGER_KEY).map(NanoLogger::logQueue).isPresent() +
            '}';
    }
}
