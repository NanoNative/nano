package org.nanonative.nano.core.model;

import org.nanonative.nano.helper.LockedBoolean;
import org.nanonative.nano.services.metric.model.MetricType;
import org.nanonative.nano.services.metric.model.MetricUpdate;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.helper.logger.logic.NanoLogger;

import java.util.Map;
import java.util.function.Supplier;

import static org.nanonative.nano.services.metric.logic.MetricService.EVENT_METRIC_UPDATE;
import static java.util.Arrays.stream;

public abstract class Service {

    protected final String name;
    protected final long createdAtMs;
    protected final LockedBoolean isReady;
    protected final NanoLogger logger = new NanoLogger(this);

    protected Service(final String name, final boolean isReady) {
        this.createdAtMs = System.currentTimeMillis();
        this.isReady = new LockedBoolean(isReady);
        this.name = name != null ? name : this.getClass().getSimpleName();
    }

    public abstract void start(final Supplier<Context> contextSub);

    public abstract void stop(final Supplier<Context> contextSub);

    public abstract Object onFailure(final Event error);

    public void onEvent(final Event event) {
        event.ifPresent(Context.EVENT_CONFIG_CHANGE, TypeMap.class, logger::configure);
    }

    public NanoLogger logger() {
        return logger;
    }

    public String name() {
        return name;
    }

    public boolean isReady() {
        return isReady.get();
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    //########## GLOBAL SERVICE METHODS ##########
    public NanoThread nanoThread(final Context context) {
        return new NanoThread().run(context.nano() != null ? context.nano().threadPool() : null, () -> context.nano() != null ? context : null, () -> {
            final long startTime = System.currentTimeMillis();
            this.logger().level(context.logLevel());
            this.logger().logQueue(context.nano().logger().logQueue());
            this.start(() -> context);
            context.nano().sendEvent(Context.EVENT_APP_SERVICE_REGISTER, context, this, null, true);
            context.sendEvent(EVENT_METRIC_UPDATE, new MetricUpdate(MetricType.GAUGE, "application.services.ready.time", System.currentTimeMillis() - startTime, Map.of("class", this.getClass().getSimpleName())), result -> {});
        }).onComplete((nanoThread, error) -> {
            if (error != null)
                context.sendEventError(new Event(Context.EVENT_APP_SERVICE_REGISTER, context, this, null), this, error);
        });
    }

    public static NanoThread[] threadsOf(final Context context, final Service... services) {
        return stream(services).map(service -> service.nanoThread(context)).toArray(NanoThread[]::new);
    }
}
