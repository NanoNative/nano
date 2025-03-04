package org.nanonative.nano.core.model;

import berlin.yuna.typemap.model.TypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.metric.model.MetricUpdate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.stream;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.services.metric.logic.MetricService.EVENT_METRIC_UPDATE;
import static org.nanonative.nano.services.metric.model.MetricType.GAUGE;

public abstract class Service {

    protected final long createdAtMs;
    protected final AtomicBoolean isReady = new AtomicBoolean(false);
    protected Context context;

    protected Service() {
        this.createdAtMs = System.currentTimeMillis();
    }

    public abstract void start();

    public abstract void stop();

    public abstract Object onFailure(final Event error);

    public abstract void onEvent(final Event event);

    public void configure(final TypeMapI<?> config) {
        configure(config, config);
    }

    public abstract void configure(final TypeMapI<?> changes, final TypeMapI<?> merged);

    public String name() {
        return this.getClass().getSimpleName();
    }

    public Context context() {
        return context;
    }

    public boolean isReady() {
        return isReady.get();
    }

    public AtomicBoolean isReadyState() {
        return isReady;
    }

    public Service context(final Context context) {
        this.context = context;
        return this;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    //########## GLOBAL SERVICE METHODS ##########
    public Service receiveEvent(final Event event) {
        if (event.channelId() == EVENT_CONFIG_CHANGE) {
            event.payloadOpt().filter(TypeMapI.class::isInstance).map(TypeMapI.class::cast)
                .or(() -> event.payloadOpt(Map.class).map(TypeMap::new).map(TypeMapI.class::cast))
                .ifPresentOrElse(configs -> {
                    final TypeMap merged = new TypeMap(context);
                    context.forEach(merged::putIfAbsent);
                    configure(configs, merged);
                    context.putAll(configs);
                }, () -> onEvent(event));
        } else {
            onEvent(event);
        }
        return this;
    }

    public NanoThread nanoThread(final Context context) {
        return new NanoThread().run(() -> context.nano() != null ? context : null, () -> {
            final long startTime = System.currentTimeMillis();
            if (!isReady.get()) {
                this.context = context.newContext(this.getClass());
                this.configure(context);
                this.start();
                this.context.broadcastEvent(EVENT_APP_SERVICE_REGISTER, () -> this);
                this.context.sendEvent(EVENT_METRIC_UPDATE, () -> new MetricUpdate(GAUGE, "application.services.ready.time", System.currentTimeMillis() - startTime, Map.of("class", this.getClass().getSimpleName())), result -> {});
                isReady.set(true);
            }
        }).onComplete((nanoThread, error) -> {
            if (error != null)
                this.context.sendEventError(context.newEvent(EVENT_APP_SERVICE_REGISTER).payload(() -> this), this, error);
        });
    }

    public static NanoThread[] threadsOf(final Context context, final Service... services) {
        return stream(services).map(service -> service.nanoThread(context)).toArray(NanoThread[]::new);
    }
}
