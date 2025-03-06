package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.LinkedTypeMap;

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
        return new LinkedTypeMap()
            .putR("id", id)
            .toJson();
    }
}
