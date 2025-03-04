package org.nanonative.nano.core;

import berlin.yuna.typemap.logic.ArgsDecoder;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.logging.LogService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static java.lang.System.lineSeparator;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.INFO;
import static org.nanonative.nano.core.model.Context.APP_HELP;
import static org.nanonative.nano.core.model.Context.CONFIG_ENV_PROD;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.NanoUtils.addConfig;
import static org.nanonative.nano.helper.NanoUtils.readConfigFiles;
import static org.nanonative.nano.helper.NanoUtils.resolvePlaceHolders;

/**
 * The abstract base class for {@link Nano} framework providing the core functionalities.
 *
 * @param <T> The type of the {@link NanoBase} implementation, used for method chaining.
 */
@SuppressWarnings({"unused", "UnusedReturnValue", "java:S2386"})
public abstract class NanoBase<T extends NanoBase<T>> {

    protected final Context context;
    protected final long createdAtMs;
    protected final LogService logService;
    protected final Map<Integer, Set<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    protected final AtomicBoolean isReady = new AtomicBoolean(true);
    protected final AtomicInteger eventCount = new AtomicInteger(0);
    @SuppressWarnings("java:S2386")
    public static final Map<Integer, String> EVENT_TYPES = new ConcurrentHashMap<>();
    public static final Map<String, String> CONFIG_KEYS = new ConcurrentHashMap<>();
    public static final AtomicInteger EVENT_ID_COUNTER = new AtomicInteger(0);

    /**
     * Initializes the NanoBase with provided configurations and arguments.
     *
     * @param configs Configuration settings in a key-value map.
     * @param args    Command line arguments.
     */
    protected NanoBase(final Map<Object, Object> configs, final String... args) {
        this.createdAtMs = System.currentTimeMillis();
        this.context = readConfigs(args);
        if (configs != null)
            configs.forEach((key, value) -> context.computeIfAbsent(convertObj(key, String.class), add -> ofNullable(value).orElse("")));
        this.logService = new LogService();
        this.logService.context(context);
        this.logService.configure(context, context);
        this.logService.start();
        this.logService.isReadyState().set(true);
        displayHelpMenu();
        subscribeEvent(EVENT_CONFIG_CHANGE, event -> event.payloadOpt(Map.class).map(this::putAll).ifPresent(nano -> event.acknowledge()));
    }

    /**
     * Creates a {@link Context} for the specified class.
     *
     * @param clazz The class for which the {@link Context} is to be created.
     * @return A new {@link Context} instance associated with the given class.
     */
    abstract Context context(final Class<?> clazz);

    /**
     * Sends an event to {@link Nano#listeners} and {@link Nano#services}.
     *
     * @param event The {@link Event} object that encapsulates the event's context, type, and payload. use {@link Event#eventOf(Context, int)} or {@link Event#asyncEventOf(Context, int)}  to create an instance.
     * @return Self for chaining
     */
    abstract T sendEvent(final Event event);

    /**
     * Processes an event with the given parameters and decides on the execution path based on the presence of a response listener and the broadcast flag.
     * If a response listener is provided, the event is processed asynchronously; otherwise, it is processed in the current thread. This method creates an {@link Event} instance and triggers the appropriate event handling logic.
     *
     * @param event The {@link Event} object that encapsulates the event's context, type, and payload. use {@link Event#eventOf(Context, int)} or {@link Event#asyncEventOf(Context, int)}  to create an instance.
     * @return An instance of {@link Event} that represents the event being processed. This object can be used for further operations or tracking.
     */
    abstract Event sendEventR(final Event event);

    public NanoBase<T> putAll(final Map<?, ?> map) {
        context.putAll(map);
        return this;
    }

    /**
     * Initiates the shutdown process for the {@link Nano} instance.
     *
     * @param clazz class for which the {@link Context} is to be created.
     * @return Self for chaining
     */
    public abstract T stop(final Class<?> clazz);

    /**
     * Initiates the shutdown process for the {@link Nano} instance.
     *
     * @param context The {@link Context} in which {@link Nano} instance shuts down.
     * @return The current instance of {@link Nano} for method chaining.
     */
    public abstract T stop(final Context context);

    /**
     * Retrieves the registered event listeners.
     *
     * @return A map of event types to their respective listeners.
     */
    public Map<Integer, Set<Consumer<Event>>> listeners() {
        return listeners;
    }

    /**
     * Registers an event listener for a specific event type.
     *
     * @param channelId The integer identifier of the event type.
     * @param listener  The consumer function that processes the {@link Event}.
     * @return Self for chaining
     */
    @SuppressWarnings({"unchecked"})
    public T subscribeEvent(final int channelId, final Consumer<Event> listener) {
        listeners.computeIfAbsent(channelId, value -> new LinkedHashSet<>()).add(listener);
        return (T) this;
    }

    /**
     * Removes a registered event listener for a specific event type.
     *
     * @param channelId The integer identifier of the event type.
     * @param listener  The consumer function to be removed.
     * @return Self for chaining
     */
    @SuppressWarnings({"unchecked"})
    public T unsubscribeEvent(final int channelId, final Consumer<Event> listener) {
        listeners.computeIfAbsent(channelId, value -> new LinkedHashSet<>()).remove(listener);
        return (T) this;
    }

    /**
     * Retrieves the process ID of the current instance.
     *
     * @return The process ID.
     */
    public long pid() {
        return ProcessHandle.current().pid();
    }

    /**
     * Calculates the memory usage of the application in megabytes.
     *
     * @return Memory usage in megabytes, rounded to two decimal places.
     */
    public double usedMemoryMB() {
        final Runtime runtime = Runtime.getRuntime();
        return BigDecimal.valueOf((double) (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Calculates the memory usage of the application in percentage.
     *
     * @return Memory usage in percentage, rounded to two decimal places.
     */
    public double heapMemoryUsage() {
        final MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return BigDecimal.valueOf((double) heapMemoryUsage.getUsed() / heapMemoryUsage.getMax()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Retrieves the creation timestamp of the instance.
     *
     * @return The timestamp of creation in milliseconds.
     */
    public long createdAtMs() {
        return createdAtMs;
    }

    /**
     * Checks whether the instance is ready for operations.
     *
     * @return readiness state.
     */
    public boolean isReady() {
        return isReady.get();
    }

    public int eventCount() {
        return eventCount.get();
    }

    /**
     * Displays a help menu with available configuration keys and their descriptions and exits.
     */
    protected void displayHelpMenu() {
        if (context.asBooleanOpt(APP_HELP).filter(helpCalled -> helpCalled).isPresent()) {
            final int keyLength = CONFIG_KEYS.keySet().stream().mapToInt(String::length).max().orElse(0);
            logService.log(() -> new LogRecord(INFO, "Available configs keys: " + lineSeparator() + CONFIG_KEYS.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(conf -> String.format("%-" + keyLength + "s  %s", conf.getKey(), conf.getValue())).collect(Collectors.joining(lineSeparator()))));
            if (context.asBooleanOpt(CONFIG_ENV_PROD).orElse(false))
                System.exit(0);
        }
    }

    /**
     * Reads and initializes {@link Context} based on provided arguments.
     *
     * @param args Command-line arguments.
     * @return The {@link Context} initialized with the configurations.
     */
    protected Context readConfigs(final String... args) {
        final Context result = readConfigFiles(null, "");
        System.getenv().forEach((key, value) -> addConfig(result, key, value));
        System.getProperties().forEach((key, value) -> addConfig(result, key, value));
        if (args != null)
            ArgsDecoder.argsOf(String.join(" ", args)).forEach((key, value) -> addConfig(result, key, value));
        return resolvePlaceHolders(result);
    }

    /**
     * Standardizes a config key.
     *
     * @param key The config key to be standardized.
     */
    @SuppressWarnings("java:S3358") // Ternary operator should not be nested
    public static String standardiseKey(final Object key) {
        return key == null ? null : convertObj(key, String.class)
            .replace('.', '_')
            .replace('-', '_')
            .replace('+', '_')
            .replace(':', '_')
            .trim()
            .toLowerCase();
    }

}
