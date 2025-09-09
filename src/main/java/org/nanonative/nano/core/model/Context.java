package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.ConcurrentTypeMap;
import berlin.yuna.typemap.model.LinkedTypeMap;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.NanoServices;
import org.nanonative.nano.core.NanoThreads;
import org.nanonative.nano.helper.ExRunnable;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.config.ConfigRegister;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpMethod;
import org.nanonative.nano.services.logging.LogFormatRegister;
import org.nanonative.nano.services.logging.model.LogLevel;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static berlin.yuna.typemap.config.TypeConversionRegister.registerTypeConvert;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.util.Optional.ofNullable;
import static org.nanonative.nano.core.model.Service.threadsOf;
import static org.nanonative.nano.helper.NanoUtils.reduceSte;
import static org.nanonative.nano.helper.event.model.Event.eventOf;
import static org.nanonative.nano.services.logging.LogService.EVENT_LOGGING;
import static org.nanonative.nano.services.logging.LogService.MAX_LOG_NAME_LENGTH;

@SuppressWarnings({"unused", "UnusedReturnValue", "java:S2160"})
public class Context extends ConcurrentTypeMap {

    // Context keys
    public static final String CONTEXT_TRACE_ID_KEY = "app_core_context_trace_id";
    public static final String CONTEXT_PARENT_KEY = "app_core_context_parent";
    public static final String CONTEXT_CLASS_KEY = "app_core_context_class";
    public static final String CONTEXT_NANO_KEY = "app_core_context_nano";
    public static final String CONTEXT_LOG_QUEUE_KEY = "app_core_context_log_queue";

    // Register configurations
    public static final String APP_HELP = ConfigRegister.registerConfig("help", "Lists available config keys");
    public static final String APP_PARAMS = ConfigRegister.registerConfig("app_params_print", "Pints all config values");
    public static final String CONFIG_PROFILES = ConfigRegister.registerConfig("app_profiles", "Active config profiles for the application");
    public static final String CONFIG_THREAD_POOL_TIMEOUT_MS = ConfigRegister.registerConfig("app_thread_pool_shutdown_timeout_ms", "Timeout for thread pool shutdown in milliseconds (see " + NanoThreads.class.getSimpleName() + ")");
    public static final String CONFIG_PARALLEL_SHUTDOWN = ConfigRegister.registerConfig("app_service_shutdown_parallel", "Enable or disable parallel service shutdown (see " + NanoServices.class.getSimpleName() + "). Enabled = Can increase the shutdown performance on`true`");
    public static final String CONFIG_OOM_SHUTDOWN_THRESHOLD = ConfigRegister.registerConfig("app_oom_shutdown_threshold", "Sets the threshold for heap in percentage to send an `EVENT_APP_OOM`. default = `98`, disabled = `-1`. If the event is unhandled, tha pp will try to shutdown with last resources");
    public static final String CONFIG_ENV_PROD = ConfigRegister.registerConfig("app_env_prod", "Enable or disable behaviour e.g. exit codes. This is useful in prod environments specially on error cases. default = `false`");

    // Register event channels
    public static final Channel<Void, Void> EVENT_APP_START = Channel.registerChannelId("APP_START", Void.class);
    public static final Channel<Void, Void> EVENT_APP_SHUTDOWN = Channel.registerChannelId("APP_SHUTDOWN", Void.class);
    public static final Channel<Service, Void> EVENT_APP_SERVICE_REGISTER = Channel.registerChannelId("APP_SERVICE_REGISTER", Service.class);
    public static final Channel<Service, Void> EVENT_APP_SERVICE_UNREGISTER = Channel.registerChannelId("APP_SERVICE_UNREGISTER", Service.class);
    public static final Channel<Scheduler, Void> EVENT_APP_SCHEDULER_REGISTER = Channel.registerChannelId("APP_SCHEDULER_REGISTER", Scheduler.class);
    public static final Channel<Scheduler, Void> EVENT_APP_SCHEDULER_UNREGISTER = Channel.registerChannelId("APP_SCHEDULER_UNREGISTER", Scheduler.class);
    public static final Channel<Object, Void> EVENT_APP_ERROR = Channel.registerChannelId("EVENT_EVENT_APP_ERROR", Object.class);
    public static final Channel<Double, Void> EVENT_APP_OOM = Channel.registerChannelId("EVENT_APP_OOM", Double.class);
    public static final Channel<Void, Void> EVENT_APP_HEARTBEAT = Channel.registerChannelId("EVENT_HEARTBEAT", Void.class);
    public static final Channel<Map, Void> EVENT_CONFIG_CHANGE = Channel.registerChannelId("EVENT_CONFIG_CHANGE", Map.class);

    static {
        // Register payload converters
        registerTypeConvert(String.class, Formatter.class, LogFormatRegister::getLogFormatter);
        registerTypeConvert(String.class, LogLevel.class, LogLevel::nanoLogLevelOf);
        registerTypeConvert(LogLevel.class, String.class, Enum::name);
        registerTypeConvert(Level.class, String.class, Level::toString);
        registerTypeConvert(String.class, Level.class, level -> LogLevel.nanoLogLevelOf(level).toJavaLogLevel());
        registerTypeConvert(ContentType.class, String.class, ContentType::name);
        registerTypeConvert(String.class, ContentType.class, ContentType::fromValue);
        registerTypeConvert(HttpMethod.class, String.class, HttpMethod::name);
        registerTypeConvert(LogRecord.class, String.class, logRecord -> new LinkedTypeMap().putR("message", logRecord.getMessage()).putR("level", LogLevel.nanoLogLevelOf(logRecord.getLevel()).name()).putR("logger", ofNullable(logRecord.getLoggerName()).map(s -> s.contains(".") ? s.substring(s.indexOf(".")) : s)).putR("thrown", ofNullable(logRecord.getThrown()).map(Throwable::getClass).map(Class::getSimpleName).orElse("false")).toJson());
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
            if (nano == null) {
                throw new IllegalArgumentException("Nano is not running");
            }
        }
        return nano;
    }

    /**
     * Retrieves the {@link Context} parent associated with this context.
     *
     * @return Parent {@link Context} or null
     */
    public Context parent() {
        return this.as(Context.class, CONTEXT_PARENT_KEY);
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
        return index < 1 ? traceId() : Stream.iterate(Optional.of(this), opt -> opt.flatMap(ctx -> ofNullable(ctx.parent())))
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
        return Stream.iterate(Optional.of(this), Optional::isPresent, opt -> opt.flatMap(ctx -> ofNullable(ctx.parent())))
            .map(opt -> opt.flatMap(ctx -> ofNullable(ctx.traceId())))
            .flatMap(Optional::stream)
            .toList();
    }

    //########## LOGGING ##########

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context fatal(final Supplier<String> message, final Object... params) {
        return log(LogLevel.FATAL, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context fatal(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.FATAL, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context error(final Supplier<String> message, final Object... params) {
        return log(LogLevel.ERROR, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context error(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.ERROR, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context warn(final Supplier<String> message, final Object... params) {
        return log(LogLevel.WARN, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context warn(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.WARN, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context info(final Supplier<String> message, final Object... params) {
        return log(LogLevel.INFO, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context info(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.INFO, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context debug(final Supplier<String> message, final Object... params) {
        return log(LogLevel.DEBUG, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context debug(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.DEBUG, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context trace(final Supplier<String> message, final Object... params) {
        return log(LogLevel.TRACE, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context trace(final Throwable thrown, final Supplier<String> message, final Object... params) {
        return log(LogLevel.TRACE, thrown, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param level   The log level to use.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context log(final LogLevel level, final Supplier<String> message, final Object... params) {
        return log(level, null, message, params);
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param level   The log level to use.
     * @param thrown  The exception to log.
     * @param message The message to log.
     * @param params  The parameters to replace in the message.
     * @return self for chaining
     */
    public Context log(final LogLevel level, final Throwable thrown, final Supplier<String> message, final Object... params) {
        newEvent(EVENT_LOGGING).async(true).broadcast(false).payload(
                () -> {
                    final LogRecord logRecord = new LogRecord(level.toJavaLogLevel(), message.get());
                    logRecord.setParameters(params);
                    logRecord.setThrown(reduceSte(thrown));
                    logRecord.setLoggerName(clazz().getCanonicalName());
                    return logRecord;
                }
            ).putR("level", level)
            .putR("name", clazz().getCanonicalName())
            .send();
        return this;
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

    //########## CHAINING HELPERS ##########

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
    public Context putR(final Object key, final Object value) {
        // ConcurrentHashMap does not allow null keys or values.
        this.put(key, value);
        return this;
    }

//    /**
//     * Registers an event listener for a specific event payload.
//     *
//     * @param channelId The integer identifier of the event payload.
//     * @param listener  The consumer function that processes the {@link Event}.
//     * @return Self for chaining
//     */
//    public Context subscribeEvent(final int channelId, final Consumer<Event> listener) {
//        nano().subscribeEvent(channelId, listener);
//        return this;
//    }

    /**
     * Registers an event listener with a typed payload.
     *
     * @param channel  The channel to be subscribed.
     * @param listener The bi-consumer to receive the {@link Event} and its payload.
     * @param <C>      The payload
     * @param <R>      The return payload
     * @return Self for chaining
     */
    public <C, R> Context subscribeEvent(final Channel<C, R> channel, final Consumer<Event<C, R>> listener) {
        nano().subscribeEvent(channel, listener);
        return this;
    }

    /**
     * Registers an event listener with a typed payload.
     *
     * @param channel  The channel to be subscribed.
     * @param filter   A predicate to filter events before passing them to the listener.
     * @param listener The bi-consumer to receive the {@link Event} and its payload.
     * @param <C>      The payload
     * @param <R>      The return payload
     * @return A consumer function that can be used to unsubscribe the listener later.
     */
    public <C, R> Consumer<Event<C, R>> subscribeEvent(final Channel<C, R> channel, final Predicate<Event<C, R>> filter, final Consumer<Event<C, R>> listener) {
        final Consumer<Event<C, R>> wrapped = event -> ofNullable(event).filter(filter).ifPresent(listener);
        nano().subscribeEvent(channel, wrapped);
        return wrapped;
    }

    /**
     * Registers an event listener with a typed payload.
     *
     * @param channel  The channel to be subscribed.
     * @param listener The bi-consumer to receive the {@link Event} and its payload.
     * @param <C>      The payload
     * @param <R>      The return payload
     * @return A consumer function that can be used to unsubscribe the listener later.
     */
    public <C, R> Consumer<Event<C, R>> subscribeEvent(final Channel<C, R> channel, final BiConsumer<Event<C, R>, C> listener) {
        return nano().subscribeEvent(channel, listener);
    }

    /**
     * Registers for global error handling.
     *
     * @param channel  The channel which should be handled on error.
     * @param listener The bi-consumer to receive the {@link Event} and its payload.
     * @param <C>      The payload
     * @param <R>      The return payload
     * @return A consumer function that can be used to unsubscribe the listener later.
     */
    public <C, R> Consumer<Event<Object, Void>> subscribeError(final Channel<C, R> channel, final Consumer<Event<C, R>> listener) {
        return nano().subscribeError(channel, listener);
    }

    /**
     * Registers for global error handling.
     *
     * @param listener The consumer to receive the {@link Event} and its payload.
     * @return A consumer function that can be used to unsubscribe the listener later.
     */
    public <C, R> Consumer<Event<Object, Void>> subscribeError(final Consumer<Event<Object, Void>> listener) {
        return nano().subscribeError(listener);
    }

    /**
     * Removes a registered event listener for a specific event payload.
     *
     * @param channel  The channel to be unsubscribed
     * @param listener The consumer function to be removed.
     * @return Self for chaining
     */
    public <C, R> Context unsubscribeEvent(final Channel<C, R> channel, final Consumer<Event<C, R>> listener) {
        return this.unsubscribeEvent(channel.id(), listener);
    }

    /**
     * Removes a registered event listener for a specific event payload.
     *
     * @param channelId The channel id to be unsubscribed
     * @param listener  The consumer function to be removed.
     * @return Self for chaining
     */
    public <C, R> Context unsubscribeEvent(final int channelId, final Consumer<Event<C, R>> listener) {
        nano().unsubscribeEvent(channelId, listener);
        return this;
    }

//
//    /**
//     * Registers an event listener with a typed payload.
//     *
//     * @param channelId The integer identifier of the event payload.
//     * @param listener  The bi-consumer to receive the {@link Event} and its payload.
//     * @return Self for chaining
//     */
//    @SuppressWarnings({"unchecked"})
//    public T subscribeEvent(final int channelId, final Consumer<Event> listener) {
//        listeners.computeIfAbsent(channelId, value -> new LinkedHashSet<>()).add(listener);
//        return (T) this;
//    }
//    /**
//     * Registers an event listener with a specific payload payload.
//     * This method provides payload-safe event handling by ensuring the payload matches the expected payload.
//     * The listener will only be called if the event payload can be cast to the specified payload.
//     *
//     * @param <T>         The payload of the event payload
//     * @param channelId   The event channel to subscribe to
//     * @param payloadType The expected class payload of the event payload
//     * @param listener    Consumer that receives both the event and the typed payload
//     * @return Self for chaining
//     */
//    public <T> Context subscribeEvent(final int channelId, final Class<T> payloadType, final BiConsumer<Event, T> listener) {
//        subscribeEvent(channelId, event -> event.payloadOpt().filter(payloadType::isInstance).map(payloadType::cast).ifPresent(payload -> listener.accept(event, payload)));
//        return this;
//    }
//
//    /**
//     * Removes a registered event listener for a specific event payload.
//     *
//     * @param channelId The integer identifier of the event payload.
//     * @param listener  The consumer function to be removed.
//     * @return Self for chaining
//     */
//    public Context unsubscribeEvent(final int channelId, final Consumer<Event> listener) {
//        nano().unsubscribeEvent(channelId, listener);
//        return this;
//    }

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
     * <code>nano.run(() -> myMethod(), LocalTime.of(7, 0, 0))</code>
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final LocalTime atTime) {
        return run(task, atTime, () -> false);
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     * <code>nano.run(() -> myMethod(), LocalTime.of(7, 0, 0))</code>
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @param until  A BooleanSupplier indicating the termination condition. <code>true</code> stops the next execution.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final LocalTime atTime, final BooleanSupplier until) {
        return run(task, atTime, null, until);
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     * <code>nano.run(() -> myMethod(), LocalTime.of(7, 0, 0))</code>
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @param until  A BooleanSupplier indicating the termination condition. <code>true</code> stops the next execution.
     * @return Self for chaining
     */
    public Context run(final ExRunnable task, final LocalTime atTime, final DayOfWeek dow, final BooleanSupplier until) {
        nano().run(() -> this, task, atTime, dow, until);
        return this;
    }

    //########## ASYNC HELPERS ##########

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param runnable function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context run(final ExRunnable... runnable) {
        runR(runnable);
        return this;
    }

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context runHandled(final Consumer<Event<Object, Void>> onFailure, final ExRunnable... runnable) {
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
        runR(services);
        return this;
    }

    //########## ASYNC RETURN HELPER ##########

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param runnable function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runR(final ExRunnable... runnable) {
        return Arrays.stream(runnable).map(task -> new NanoThread().run(() -> this, task)).toArray(NanoThread[]::new);
    }

    /**
     * Executes one or multiple runnable asynchronously.
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runReturnHandled(final Consumer<Event<Object, Void>> onFailure, final ExRunnable... runnable) {
        return Arrays.stream(runnable).map(task -> new NanoThread()
            .onComplete((thread, error) -> {
                if (error != null)
                    onFailure.accept(newEvent(EVENT_APP_ERROR).error(error).payload(() -> thread));
            }).run(() -> this, task)
        ).toArray(NanoThread[]::new);
    }

    /**
     * Executes one or multiple {@link Service} asynchronously.
     *
     * @param services The {@link Service} to be appended.
     * @return {@link NanoThread}s
     */
    public NanoThread[] runR(final Service... services) {
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
        NanoThread.waitFor(runR(runnable));
        return this;
    }

    /**
     * Executes asynchronously and waits for all runnable to be ready
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return The {@link Context} object for chaining further operations.
     */
    public final Context runAwaitHandled(final Consumer<Event<Object, Void>> onFailure, final ExRunnable... runnable) {
        NanoThread.waitFor(runReturnHandled(onFailure, runnable));
        return this;
    }

    /**
     * Executes asynchronously and waits for all {@link Service} to be ready
     *
     * @return The {@link Context} object for chaining further operations.
     */
    public Context runAwait(final Service... services) {
        runAwaitR(services);
        return this;
    }

    //########## ASYNC AWAIT HELPER RETURN ##########

    /**
     * Executes asynchronously and waits for all {@link Service} to be ready
     *
     * @param runnable function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runAwaitR(final ExRunnable... runnable) {
        return NanoThread.waitFor(runR(runnable));
    }

    /**
     * Executes and waits for all {@link Service} to be ready
     *
     * @param onFailure function to execute on failure
     * @param runnable  function to execute.
     * @return {@link NanoThread}s
     */
    public final NanoThread[] runAwaitRHandled(final Consumer<Event<Object, Void>> onFailure, final ExRunnable... runnable) {
        return NanoThread.waitFor(runReturnHandled(onFailure, runnable));
    }

    /**
     * Executes and waits for all {@link Service} to be ready
     *
     * @return {@link NanoThread}s
     */
    public NanoThread[] runAwaitR(final Service... services) {
        return NanoThread.waitFor(runR(services));
    }

    //########## EVENT HELPER ##########

    /**
     * Sends an unhandled event with the provided, nullable payload and exception. If the event is not acknowledged, the error message is logged.
     *
     * @param payloadOrEvent The payload of the unhandled event or object, containing data relevant to the event's context and purpose.
     * @param throwable      The exception that occurred during the event processing.
     * @return self for chaining
     */
    public Context sendEventError(final Object payloadOrEvent, final Throwable throwable) {
        if (payloadOrEvent instanceof final Event<?, ?> evt) {
            if (evt.channel() == EVENT_APP_ERROR)
                return error(throwable, () -> "Event [{}] looped.", evt.channel().name());
            if (!newEvent(EVENT_APP_ERROR).payload(() -> evt).error(throwable).containsEvent(true).send().isAcknowledged())
                return log(evt.asOpt(LogLevel.class, "level").orElse(LogLevel.ERROR), throwable, () -> "Event [{}] went rogue.", evt.channel().name());
        } else if (!newEvent(EVENT_APP_ERROR).payload(() -> payloadOrEvent).error(throwable).send().isAcknowledged()) {
            return error(throwable, () -> "Event [{}] went rogue.", EVENT_APP_ERROR.name());
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
    public Context sendEventError(final Event<?, ?> event, final Service service, final Throwable throwable) {
        // loop prevention
        if (event.channel() == EVENT_APP_ERROR)
            event.context().error(throwable, () -> "Unhandled event [{}] service [{}]", event.channel().name(), service.name());
        if (service.onFailure(event.error(throwable)) == null) {
            event.context().sendEventError(event, throwable);
        }
        return this;
    }

    /**
     * Creates a new {@link Event} instance with the specified event payload.
     *
     * @param channel The {@link Channel} for the event.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public <C, R> Event<C, R> newEvent(final Channel<C, R> channel) {
        return eventOf(this, channel);
    }

    /**
     * Creates a new {@link Event} instance with the specified event payload.
     *
     * @param channel The {@link Channel} for the event.
     * @param payload A supplier that provides the payload for the event. This allows for lazy evaluation of the payload, which can be useful for performance or when the payload is not immediately available.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    public <C, R> Event<C, R> newEvent(final Channel<C, R> channel, final Supplier<C> payload) {
        return eventOf(this, channel).payload(payload);
    }

    /**
     * Registers a new {@link Channel} with a given name if it does not already exist.
     * If the {@link Channel} payload already exists, it returns the existing {@link Channel}.
     *
     * @param name    The name of the {@link Channel} payload to register.
     * @param payload The class type of the payload for the {@link Channel}.
     * @return The {@link Channel}  of the newly registered event payload, or the {@link Channel}  of the existing event payload if it already exists. Returns null if the input is null or empty.
     */
    public <C> Channel<C, Void> registerChannelId(final String name, final Class<C> payload) {
        return Channel.registerChannelId(name, payload);
    }

    /**
     * Registers a new {@link Channel} with a given name if it does not already exist.
     * If the {@link Channel} payload already exists, it returns the existing {@link Channel}.
     *
     * @param name     The name of the {@link Channel} payload to register.
     * @param payload  The class type of the payload for the {@link Channel}.
     * @param response The class type of the response for the {@link Channel}.
     * @return The {@link Channel}  of the newly registered event payload, or the {@link Channel}  of the existing event payload if it already exists. Returns null if the input is null or empty.
     */
    public <C, R> Channel<C, R> registerChannelId(final String name, final Class<C> payload, final Class<R> response) {
        return Channel.registerChannelId(name, payload, response);
    }

    /**
     * Retrieves a {@link Service} of a specified payload.
     *
     * @param <S>          The payload of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the {@link Service} to retrieve.
     * @return The first instance of the specified {@link Service}, or null if not found.
     */
    public <S extends Service> S service(final Class<S> serviceClass) {
        return nano().service(serviceClass);
    }

    /**
     * Retrieves a list of services of a specified payload.
     *
     * @param <S>          The payload of the service to retrieve, which extends {@link Service}.
     * @param serviceClass The class of the service to retrieve.
     * @return A list of services of the specified payload. If no services of this payload are found,
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
        this.put(CONTEXT_NANO_KEY, parent != null ? parent.as(Nano.class, CONTEXT_NANO_KEY) : null);
        this.put(CONTEXT_CLASS_KEY, resolvedClass);
        this.put(CONTEXT_TRACE_ID_KEY, (resolvedClass.getSimpleName()) + "/" + UUID.randomUUID().toString().replace("-", ""));
        if (parent != null)
            this.put(CONTEXT_PARENT_KEY, parent);
        MAX_LOG_NAME_LENGTH.updateAndGet(length -> Math.max(length, resolvedClass.getSimpleName().length()));
    }

    private Class<?> clazz() {
        return this.asOpt(Class.class, CONTEXT_CLASS_KEY).orElse(Context.class);
    }

    public void tryExecute(final ExRunnable operation) {
        tryExecute(operation, null);
    }

    public void tryExecute(final ExRunnable operation, final Consumer<Throwable> consumer) {
        NanoUtils.tryExecute(() -> this, operation, consumer);
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("size", size())
            .putR("logger", ofNullable(clazz()).map(Class::getSimpleName).orElse(null))
            .putR("class", this.getClass().getSimpleName())
            .toJson();
    }
}
