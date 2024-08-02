package org.nanonative.nano.core.model;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class Scheduler extends ScheduledThreadPoolExecutor {
    private final String id;

    public Scheduler(final String id) {
        this(id, 1, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public Scheduler(final String id, final int corePoolSize, final RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Scheduler{" +
            "id='" + id + '\'' +
            '}';
    }
}
