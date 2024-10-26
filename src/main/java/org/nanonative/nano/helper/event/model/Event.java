package org.nanonative.nano.helper.event.model;

import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.logger.logic.NanoLogger;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.Nano;

import java.util.Optional;
import java.util.function.Consumer;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static java.util.Optional.ofNullable;
import static org.nanonative.nano.helper.event.EventChannelRegister.eventNameOf;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Event {

    protected int channelId;
    protected final long createdAtMs;
    protected final Context context;
    protected final Consumer<Object> responseListener;
    protected final Object payload;
    protected TypeMap cache;
    protected Object response;
    protected Throwable error;

    public static final String EVENT_ORIGINAL_CHANNEL_ID = "app_original_event_channel_id";

    /**
     * Constructs an instance of the Event class with specified type, context, payload, and response listener.
     * This event object can be used to trigger specific actions or responses based on the event type and payload.
     *
     * @param channelId        The integer representing the type of the event. This typically corresponds to a specific kind of event.
     * @param context          The {@link Context} in which the event is created and processed. It provides environmental data and configurations.
     * @param payload          The data or object that is associated with this event. This can be any relevant information that needs to be passed along with the event.
     * @param responseListener A consumer that handles the response of the event processing. It can be used to execute actions based on the event's outcome or data.
     */
    public Event(final int channelId, final Context context, final Object payload, final Consumer<Object> responseListener) {
        this.context = context;
        this.channelId = channelId;
        this.responseListener = responseListener;
        this.payload = payload;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String name() {
        return eventNameOf(channelId);
    }

    public String nameOrg() {
        return eventNameOf(channelIdOrg());
    }

    public Nano nano() {
        return context.nano();
    }

    public int channelId() {
        return channelId;
    }

    public int channelIdOrg() {
        return ofNullable(cache).flatMap(c -> c.getOpt(Integer.class, EVENT_ORIGINAL_CHANNEL_ID)).orElse(channelId);
    }

    public Event channelId(final int channelId) {
        this.channelId = channelId;
        return this;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    public Event ifPresent(final int channelId, final Consumer<Event> consumer) {
        if (this.channelId == channelId) {
            consumer.accept(this);
        }
        return this;
    }

    public Event ifPresentAck(final int channelId, final Consumer<Event> consumer) {
        if (this.channelId == channelId) {
            consumer.accept(this);
            acknowledge();
        }
        return this;
    }

    public <T> Event ifPresent(final int channelId, final Class<T> clazz, final Consumer<T> consumer) {
        if (this.channelId == channelId) {
            final T payloadObj = payload(clazz);
            if (payloadObj != null)
                consumer.accept(payloadObj);
        }
        return this;
    }

    public <T> Event ifPresentAck(final int channelId, final Class<T> clazz, final Consumer<T> consumer) {
        if (this.channelId == channelId) {
            final T payloadObj = payload(clazz);
            if (payloadObj != null) {
                consumer.accept(payloadObj);
                acknowledge();
            }
        }
        return this;
    }

    public NanoLogger logger() {
        return context.logger();
    }

    public Object payload() {
        return payload;
    }

    public boolean isAcknowledged() {
        return response != null;
    }

    public Optional<Object> payloadOpt() {
        return ofNullable(payload);
    }

    public <T> T payload(final Class<T> type) {
        return convertObj(payload, type);
    }

    public <T> Optional<T> payloadOpt(final Class<T> type) {
        return ofNullable(convertObj(payload, type));
    }

    public Context context() {
        return context;
    }

    public Consumer<Object> responseListener() {
        return responseListener;
    }

    public Event acknowledge() {
        return acknowledge(null);
    }

    public Event acknowledge(final Runnable response) {
        if (response != null)
            response.run();
        return response(true);
    }

    public Event response(final Object response) {
        if (responseListener != null) {
            responseListener.accept(response);
        }
        this.response = response;
        return this;
    }

    public <T> T response(final Class<T> type) {
        return convertObj(response, type);
    }

    public <T> Optional<T> responseOpt(final Class<T> type) {
        return ofNullable(response(type));
    }

    public Object response() {
        return response;
    }

    public Event peek(final Consumer<Event> peek) {
        if (peek != null)
            peek.accept(this);
        return this;
    }

    public Event put(final Object key, final Object value) {
        context.put(key, value);
        return this;
    }

    public Event cache(final Object key, final Object value) {
        cache().put(key, value);
        return this;
    }

    public TypeMap cache() {
        if (cache == null) {
            cache = new TypeMap();
        }
        return cache;
    }

    public Throwable error() {
        return error;
    }

    public Event error(final Throwable error) {
        this.error = error;
        return this;
    }

    @Override
    public String toString() {
        return "Event{" +
            "name=" + name() +
            ", ack=" + (response != null) +
            ", listener=" + (response != null) +
            ", context=" + context.size() +
            ", createdAtMs=" + createdAtMs +
            '}';
    }
}
