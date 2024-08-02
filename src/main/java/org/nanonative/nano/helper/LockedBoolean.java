package org.nanonative.nano.helper;

import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * A thread-safe class that manages a boolean state with synchronized actions.
 * Unlike {@link java.util.concurrent.atomic.AtomicBoolean}, LockedBoolean allows executing complex
 * synchronized actions based on the current state and conditionally updating the state within a locked context.
 * This class is designed for scenarios where actions need to be performed during state changes,
 * ensuring thread safety and atomicity of operations.
 */
public class LockedBoolean {
    private boolean state;
    private final Semaphore lock = new Semaphore(1);

    /**
     * Constructs a new LockedBoolean with the initial state set to false.
     */
    public LockedBoolean() {
        this(false);
    }

    /**
     * Constructs a new LockedBoolean with the specified initial state.
     *
     * @param state the initial state
     */
    public LockedBoolean(final boolean state) {
        this.state = state;
    }

    /**
     * Sets the state to the specified value with thread safety.
     *
     * @param state the new state value
     */
    public void set(final boolean state) {
        execute(null, state, null);
    }

    /**
     * Sets the state to the specified value with thread safety.
     *
     * @param state the new state value
     * @param run   The {@link Consumer} to execute while setting new state.
     */
    public void set(final boolean state, final Consumer<Boolean> run) {
        execute(null, state, run);
    }

    /**
     * Conditionally sets the state based on a condition.
     *
     * @param when The condition that determines if the state should be set to {@code then}.
     * @param then The state to set if {@code when} is true.
     */
    public void set(final boolean when, final boolean then) {
        set(when, then, null);
    }

    /**
     * Conditionally sets the state and executes a {@link Consumer} based on a condition.
     *
     * @param when The condition that determines if the state should be set to {@code then}.
     * @param then The state to set if {@code when} is true.
     * @param run  The {@link Consumer} to execute if the condition {@code when} is true.
     */
    public void set(final boolean when, final boolean then, final Consumer<Boolean> run) {
        execute(when, then, run);
    }

    /**
     * Executes a {@link Consumer} based on the current state.
     *
     * @param run The Runnable to execute if the current state is true.
     */
    public void run(final Consumer<Boolean> run) {
        execute(null, null, run);
    }

    /**
     * Executes a {@link Consumer} based on a condition without changing the state.
     *
     * @param when The condition that determines which Runnable to execute.
     * @param then The {@link Consumer} to execute if {@code when} is true.
     */
    public void run(final boolean when, final Consumer<Boolean> then) {
        execute(when, null, then);
    }

    /**
     * Retrieves the current state with thread safety.
     *
     * @return the current boolean state
     */
    public boolean get() {
        try {
            lock.acquire();
            return state;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return state;
        } finally {
            lock.release();
        }
    }

    /**
     * Executes actions conditionally based on the expected state and optionally updates the state.
     * This private method is the core logic for state management and action execution.
     *
     * @param expected The expected state to trigger execution, or null if execution should not be conditional.
     * @param newValue The new state to set if execution occurs, or null if the state should not change.
     * @param run      The {@link Consumer} to execute if expected matches the condition.
     */
    private void execute(final Boolean expected, final Boolean newValue, final Consumer<Boolean> run) {
        try {
            lock.acquire();
            if (expected == null || state == expected) {
                if (run != null) {
                    run.accept(state);
                }
                if (newValue != null)
                    state = newValue;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.release();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "state=" + get() +
            '}';
    }
}

