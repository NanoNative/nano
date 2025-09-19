package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.LinkedTypeMap;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Lightweight scheduler for deferred and periodic tasks.
 */
public class Scheduler extends ScheduledThreadPoolExecutor {
    private final String id;

    /**
     * Creates a new Scheduler.
     * @param id a custom defined id
     */
    public Scheduler(final String id) {
        this(id, 1, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Creates a new Scheduler.
     * @param id a custom defined id
     * @param corePoolSize the number of threads to keep in the pool, even if they are idle
     * @param handler the handler
     */
    public Scheduler(final String id, final int corePoolSize, final RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
        this.id = id;
    }

    /**
     * Performs the id operation.
     * @return the result
     */
    public String id() {
        return id;
    }

    /**
     * Performs the toString operation.
     * @return the result
     */
    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("id", id)
            .putR("class", this.getClass().getSimpleName())
            .toJson();
    }
}
