package org.nanonative.nano.core.model;

import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.helper.logger.logic.NanoLogger;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.stream;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_REGISTER;
import static org.nanonative.nano.services.metric.logic.MetricService.EVENT_METRIC_UPDATE;
import static org.nanonative.nano.services.metric.model.MetricType.GAUGE;

public abstract class Service {

    protected final long createdAtMs;
    protected final AtomicBoolean isReady = new AtomicBoolean(false);
    protected Context context;
    protected NanoLogger log;

    protected Service() {
        this.createdAtMs = System.currentTimeMillis();
    }

    public abstract void start();

    public abstract void stop();

    public abstract Object onFailure(final Event error);

    public abstract void onEvent(final Event event);

    public String name() {
        return this.getClass().getSimpleName();
    }

    public Context context() {
        return context;
    }

    public NanoLogger log() {
        return log;
    }

    public boolean isReady() {
        return isReady.get();
    }

    public AtomicBoolean isReadyState() {
        return isReady;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    //########## GLOBAL SERVICE METHODS ##########
    public NanoThread nanoThread(final Context context) {
        return new NanoThread().run(context.nano() != null ? context.nano().threadPool() : null, () -> context.nano() != null ? context : null, () -> {
            final long startTime = System.currentTimeMillis();
            if (isReady.compareAndSet(false, true)) {
                this.context = context.newContext(this.getClass());
                this.log = context.logger();
                this.start();
                this.context.broadcastEvent(EVENT_APP_SERVICE_REGISTER, this);
                this.context.sendEvent(EVENT_METRIC_UPDATE, new MetricUpdate(GAUGE, "application.services.ready.time", System.currentTimeMillis() - startTime, Map.of("class", this.getClass().getSimpleName())), result -> {});
            }
        }).onComplete((nanoThread, error) -> {
            if (error != null)
                this.context.sendEventError(new Event(EVENT_APP_SERVICE_REGISTER, context, this, null), this, error);
        });
    }

    public static NanoThread[] threadsOf(final Context context, final Service... services) {
        return stream(services).map(service -> service.nanoThread(context)).toArray(NanoThread[]::new);
    }
}
