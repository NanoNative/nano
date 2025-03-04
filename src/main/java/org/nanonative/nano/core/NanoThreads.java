package org.nanonative.nano.core;

import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Scheduler;
import org.nanonative.nano.helper.ExRunnable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableSet;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.nanonative.nano.core.model.Context.CONFIG_THREAD_POOL_TIMEOUT_MS;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SCHEDULER_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SCHEDULER_UNREGISTER;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.helper.NanoUtils.callerInfoStr;
import static org.nanonative.nano.helper.NanoUtils.getThreadName;
import static org.nanonative.nano.helper.NanoUtils.handleJavaError;
import static org.nanonative.nano.helper.event.model.Event.eventOf;

/**
 * The abstract base class for {@link Nano} framework providing thread handling functionalities.
 *
 * @param <T> The type of the {@link NanoThreads} implementation, used for method chaining.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public abstract class NanoThreads<T extends NanoThreads<T>> extends NanoBase<T> {

    protected final Set<ScheduledExecutorService> schedulers;

    /**
     * Initializes {@link NanoThreads} with configurations and command-line arguments.
     *
     * @param config Configuration parameters for the {@link NanoThreads} instance.
     * @param args   Command-line arguments passed during the application start.
     */
    protected NanoThreads(final Map<Object, Object> config, final String... args) {
        super(config, args);
        this.schedulers = ConcurrentHashMap.newKeySet();
        subscribeEvent(EVENT_APP_SCHEDULER_REGISTER, event -> event.payloadOpt(ScheduledExecutorService.class).map(schedulers::add).ifPresent(nano -> event.acknowledge()));
        subscribeEvent(EVENT_APP_SCHEDULER_UNREGISTER, event -> event.payloadOpt(ScheduledExecutorService.class).map(scheduler -> {
            scheduler.shutdown();
            schedulers.remove(scheduler);
            return this;
        }).ifPresent(nano -> event.acknowledge()));
    }

    public static void runAsync(final Runnable task) {
        GLOBAL_THREAD_POOL.submit(task);
    }

    /**
     * Provides an unmodifiable set of {@link ScheduledExecutorService}.
     *
     * @return An unmodifiable set of {@link ScheduledExecutorService} instances.
     */
    public Set<ScheduledExecutorService> schedulers() {
        return unmodifiableSet(schedulers);
    }

    /**
     * Executes a task asynchronously after a specified delay.
     *
     * @param task     The task to execute.
     * @param delay    The delay before executing the task.
     * @param timeUnit The time unit of the delay parameter.
     * @return Self for chaining
     */
    @SuppressWarnings({"resource", "unchecked"})
    public T run(final Supplier<Context> context, final ExRunnable task, final long delay, final TimeUnit timeUnit) {
        final Scheduler scheduler = asyncFromPool(context);
        scheduler.schedule(() -> executeScheduler(context, task, scheduler, false), delay, timeUnit);
        return (T) this;
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
    @SuppressWarnings({"resource", "unchecked"})
    public T run(final Supplier<Context> context, final ExRunnable task, final long delay, final long period, final TimeUnit unit, final BooleanSupplier until) {
        final Scheduler scheduler = asyncFromPool(context);

        // Periodic task
        scheduler.scheduleAtFixedRate(() -> {
            if (until.getAsBoolean()) {
                scheduler.shutdown();
            } else {
                executeScheduler(context, task, scheduler, true);
            }
        }, delay, period, unit);
        return (T) this;
    }

    /**
     * Executes a task periodically, starting after an initial delay.
     * <code>nano.run(() -> myMethod(), LocalTime.of(7, 0, 0))</code>
     *
     * @param task   The task to execute.
     * @param atTime The time of hour/minute/second to start the task.
     * @param dow    The day of the week to start the task.
     * @param until  A BooleanSupplier indicating the termination condition. <code>true</code> stops the next execution.
     * @return Self for chaining
     */
    //TODO: it should not run if the time is already passed - happens currently on dow
    public T run(final Supplier<Context> context, final ExRunnable task, final LocalTime atTime, final DayOfWeek dow, final BooleanSupplier until) {
        final ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextRun = now.withHour(atTime.getHour()).withMinute(atTime.getMinute()).withSecond(atTime.getSecond());
        if (dow != null)
            nextRun = nextRun.with(TemporalAdjusters.nextOrSame(dow));

        return run(context, task, Duration.between(now, nextRun).getSeconds(), DAYS.toSeconds(1), SECONDS, until);
    }

    /**
     * Creates a {@link Scheduler} from the thread pool.
     *
     * @return The newly created {@link Scheduler}.
     */
    protected Scheduler asyncFromPool(final Supplier<Context> context) {
        final String schedulerId = callerInfoStr(this.getClass()) + "_" + UUID.randomUUID();
        final Scheduler scheduler = new Scheduler(schedulerId) {
            @Override
            protected void beforeExecute(final Thread t, final Runnable r) {
                t.setName("Scheduler_" + schedulerId);
                try {
                    GLOBAL_THREAD_POOL.submit(r);
                } catch (final Throwable error) {
                    handleJavaError(context, error);
                }
            }
        };
        eventOf(context(Scheduler.class), EVENT_APP_SCHEDULER_REGISTER).payload(() -> scheduler).broadcast(true).async(true).send();
        return scheduler;
    }

    /**
     * Shuts down all threads and scheduled executors gracefully.
     */
    protected void shutdownThreads() {
        final long timeoutMs = context.asLongOpt(CONFIG_THREAD_POOL_TIMEOUT_MS).filter(l -> l > 0).orElse(500L);
        context.debug(() -> "Shutdown schedulers [{}]", schedulers.size());
        shutdownExecutors(timeoutMs, schedulers.toArray(ScheduledExecutorService[]::new));
    }

    /**
     * Shuts down executors and handles timeout for forced termination.
     *
     * @param timeoutMs        The maximum time to wait for executor termination.
     * @param executorServices An array of ExecutorService instances to shut down.
     */
    protected void shutdownExecutors(final long timeoutMs, final ExecutorService... executorServices) {
        Arrays.stream(executorServices).forEach(ExecutorService::shutdown);

        Arrays.stream(executorServices).parallel().forEach(executorService -> {
            executorService.shutdown();
            try {
                kill(executorService, timeoutMs);
                removeScheduler(executorService);
            } catch (final InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Removes a scheduler from the set of managed schedulers.
     *
     * @param executorService The {@link ExecutorService} to remove from the scheduler set.
     */
    protected void removeScheduler(final ExecutorService executorService) {
        if (executorService instanceof final ScheduledExecutorService scheduler) {
            schedulers.remove(scheduler);
        }
    }

    /**
     * Forces shutdown of an {@link ExecutorService} if it doesn't terminate within the specified timeout.
     *
     * @param executorService The executor service to shut down.
     * @param timeoutMs       The maximum time to wait for termination.
     * @throws InterruptedException if interrupted while waiting.
     */
    protected void kill(final ExecutorService executorService, final long timeoutMs) throws InterruptedException {
        if (!executorService.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
            context.debug(() -> "Kill [{}]", getThreadName(executorService));
            executorService.shutdownNow();
            if (!executorService.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                context.warn(() -> "[{}] did not terminate. Is this a glitch in the Matrix?", getThreadName(executorService));
            }
        }
    }

    protected void executeScheduler(final Supplier<Context> context, final ExRunnable task, final Scheduler scheduler, final boolean periodically) {
        try {
            task.run();
            if (!periodically)
                eventOf(context(this.getClass()), EVENT_APP_SCHEDULER_UNREGISTER).payload(() -> scheduler).async(true).send();
        } catch (final Throwable e) {
            handleJavaError(context, e);
            eventOf(context(this.getClass()), EVENT_APP_SCHEDULER_UNREGISTER).payload(() -> scheduler).broadcast(true).async(true).send();
            context(this.getClass()).sendEventError(scheduler, e);
        }
    }

}
