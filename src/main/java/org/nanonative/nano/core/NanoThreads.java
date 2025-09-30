package org.nanonative.nano.core;

import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Scheduler;
import org.nanonative.nano.helper.ExRunnable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.nanonative.nano.core.model.Context.CONFIG_THREAD_POOL_TIMEOUT_MS;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SCHEDULER_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SCHEDULER_UNREGISTER;
import static org.nanonative.nano.helper.NanoUtils.callerInfoStr;
import static org.nanonative.nano.helper.NanoUtils.getThreadName;
import static org.nanonative.nano.helper.NanoUtils.handleJavaError;

/**
 * The abstract base class for {@link Nano} framework providing thread handling functionalities.
 *
 * @param <T> The payload of the {@link NanoThreads} implementation, used for method chaining.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public abstract class NanoThreads<T extends NanoThreads<T>> extends NanoBase<T> {

    protected final Set<ScheduledExecutorService> schedulers;
    protected final Thread keepAliveThread;

    /**
     * Initializes {@link NanoThreads} with configurations and command-line arguments.
     *
     * @param config Configuration parameters for the {@link NanoThreads} instance.
     * @param args   Command-line arguments passed during the application start.
     */
    protected NanoThreads(final Map<Object, Object> config, final String... args) {
        super(config, args);
        this.schedulers = ConcurrentHashMap.newKeySet();
        subscribeEvent(EVENT_APP_SCHEDULER_REGISTER, event -> schedulers.add(event.payloadAck()));
        subscribeEvent(EVENT_APP_SCHEDULER_UNREGISTER, (event, scheduler) -> {
            scheduler.shutdown();
            schedulers.remove(scheduler);
            event.acknowledge();
        });
        keepAliveThread = new Thread(() -> {
            try {
                Thread.sleep(Long.MAX_VALUE); // ~292 billion years
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "nano-keepalive");
        keepAliveThread.setDaemon(false);
        keepAliveThread.start();
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
    public T run(final Supplier<Context> context, final ExRunnable task, final LocalTime atTime, final DayOfWeek dow, final BooleanSupplier until) {
        return run(context, task, atTime, dow, ZoneId.systemDefault(), until);
    }

    /**
     * Executes a task daily at the specified wall-clock time in the server's
     * default time zone until the stop condition returns {@code true}.
     *
     * @param context the execution context supplier
     * @param task    the task to execute
     * @param atTime  the daily wall-clock time (hour, minute, second)
     * @param until   stop condition; when {@code true}, cancels further runs
     * @return self for chaining
     */
    public T runDaily(final Supplier<Context> context, final ExRunnable task, final LocalTime atTime, final BooleanSupplier until) {
        return run(context, task, atTime, null, ZoneId.systemDefault(), until);
    }

    /**
     * Executes a task weekly at the given day of week and wall-clock time
     * in the server's default time zone until the stop condition returns {@code true}.
     *
     * @param context the execution context supplier
     * @param task    the task to execute
     * @param dow     the day of the week
     * @param atTime  the weekly wall-clock time (hour, minute, second)
     * @param until   stop condition; when {@code true}, cancels further runs
     * @return self for chaining
     */
    public T runWeekly(final Supplier<Context> context, final ExRunnable task, final DayOfWeek dow, final LocalTime atTime, final BooleanSupplier until) {
        return run(context, task, atTime, dow, ZoneId.systemDefault(), until);
    }

    /**
     * Core scheduling method for daily or weekly execution at a fixed wall-clock time.
     * Uses {@link ZonedDateTime} in the given zone to account for daylight saving changes.
     *
     * @param context the execution context supplier
     * @param task    the task to execute
     * @param atTime  the wall-clock time (hour, minute, second)
     * @param dow     optional day of week; if {@code null}, runs every day
     * @param zone    the time zone (usually {@link ZoneId#systemDefault()})
     * @param until   stop condition; when {@code true}, cancels further runs
     * @return self for chaining
     */
    public T run(final Supplier<Context> context, final ExRunnable task, final LocalTime atTime, final DayOfWeek dow, final ZoneId zone, final BooleanSupplier until) {
        scheduleOnce(context, task, asyncFromPool(), until, atTime, dow, zone, initialPlanned(atTime, dow, zone));
        return (T) this;
    }

    /**
     * Internal recursive scheduler: schedules the given task once,
     * then reschedules itself for the next occurrence.
     *
     * @param context   the execution context supplier
     * @param task      the task to execute
     * @param scheduler the scheduler executor
     * @param until     stop condition; when {@code true}, cancels further runs
     * @param atTime    the wall-clock time (hour, minute, second)
     * @param dow       optional day of week; if {@code null}, runs every day
     * @param zone      the time zone
     * @param planned   the planned execution time
     */
    protected void scheduleOnce(final Supplier<Context> context, final ExRunnable task, final Scheduler scheduler, final BooleanSupplier until, final LocalTime atTime, final DayOfWeek dow, final ZoneId zone, final ZonedDateTime planned) {
        final long delayMs = Math.max(1L, Duration.between(ZonedDateTime.now(zone), planned).toMillis());

        scheduler.schedule(() -> {
            if (ofNullable(until).map(BooleanSupplier::getAsBoolean).filter(end -> end).isPresent()) {
                scheduler.shutdown();
                return;
            }
            executeScheduler(context, task, scheduler, true);

            // compute next run from planned time, not from "now"
            scheduleOnce(context, task, scheduler, until, atTime, dow, zone, nextPlanned(planned, atTime, dow, zone));
        }, delayMs, MILLISECONDS);
    }

    /**
     * Calculates the first execution time after now for the given
     * wall-clock time and day of week.
     *
     * @param atTime the wall-clock time
     * @param dow    optional day of week; if {@code null}, runs daily
     * @param zone   the time zone
     * @return the first valid planned execution time
     */
    protected static ZonedDateTime initialPlanned(final LocalTime atTime, final DayOfWeek dow, final ZoneId zone) {
        final ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime candidate = resolveWallTime(now.toLocalDate(), atTime, zone);

        if (dow != null) {
            candidate = candidate.with(TemporalAdjusters.nextOrSame(dow));
            if (candidate.isBefore(now)) {
                candidate = resolveWallTime(candidate.toLocalDate().plusWeeks(1), atTime, zone);
            }
        } else if (candidate.isBefore(now)) {
            candidate = resolveWallTime(now.toLocalDate().plusDays(1), atTime, zone);
        }
        return candidate;
    }

    /**
     * Calculates the next execution time after a previous planned run.
     *
     * @param prevPlanned the previous planned run time
     * @param atTime      the wall-clock time
     * @param dow         optional day of week; if {@code null}, runs daily
     * @param zone        the time zone
     * @return the next planned execution time
     */
    protected static ZonedDateTime nextPlanned(final ZonedDateTime prevPlanned, final LocalTime atTime, final DayOfWeek dow, final ZoneId zone) {
        return dow != null ? resolveWallTime(prevPlanned.toLocalDate().plusWeeks(1), atTime, zone)
            : resolveWallTime(prevPlanned.toLocalDate().plusDays(1), atTime, zone);
    }

    /**
     * DST-safe wall time: pushes through gaps, picks the earlier offset in overlaps.
     */
    protected static ZonedDateTime resolveWallTime(final LocalDate date, final LocalTime time, final ZoneId zone) {
        return ZonedDateTime.ofLocal(LocalDateTime.of(date, time), zone, null);
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
        final Scheduler scheduler = asyncFromPool();
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
        final Scheduler scheduler = asyncFromPool();

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
     * Creates a {@link Scheduler} from the thread pool.
     *
     * @return The newly created {@link Scheduler}.
     */
    protected Scheduler asyncFromPool() {
        final String schedulerId = callerInfoStr(this.getClass()) + "_" + UUID.randomUUID();
        final Scheduler scheduler = new Scheduler(schedulerId) {
            @Override
            protected void beforeExecute(final Thread t, final Runnable r) {
                t.setName("Scheduler_" + schedulerId);
            }
        };
        context(Scheduler.class).newEvent(EVENT_APP_SCHEDULER_REGISTER, () -> scheduler).broadcast(true).async(true).send();
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
        if (!executorService.awaitTermination(timeoutMs, MILLISECONDS)) {
            context.debug(() -> "Kill [{}]", getThreadName(executorService));
            executorService.shutdownNow();
            if (!executorService.awaitTermination(timeoutMs, MILLISECONDS)) {
                context.warn(() -> "[{}] did not terminate. Is this a glitch in the Matrix?", getThreadName(executorService));
            }
        }
    }

    /**
     * Executes the task.
     *
     * @param context      the context
     * @param task         the task
     * @param scheduler    the scheduler
     * @param periodically the periodically
     */
    protected void executeScheduler(final Supplier<Context> context, final ExRunnable task, final Scheduler scheduler, final boolean periodically) {
        final Context ctx = ofNullable(context).map(Supplier::get).orElse(this.context);
        try {
            task.run();
            if (!periodically)
                ctx.newEvent(EVENT_APP_SCHEDULER_UNREGISTER, () -> scheduler).broadcast(true).async(true).send();
        } catch (final Throwable e) {
            handleJavaError(context, e);
            ctx.newEvent(EVENT_APP_SCHEDULER_UNREGISTER, () -> scheduler).broadcast(true).async(true).send();
            ctx.sendEventError(scheduler, e);
        }
    }

}
