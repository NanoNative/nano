package org.nanonative.nano.helper.event.model;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.Type;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static java.util.Optional.ofNullable;
import static org.nanonative.nano.helper.event.EventChannelRegister.eventNameOf;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Event extends TypeMap {

    protected int channelId;
    protected final Context context;
    protected transient Consumer<Object> responseListener;
    protected transient Supplier<Object> payload;
    protected transient Object payloadRaw;
    protected transient Object response;
    protected Throwable error;

    public static final String EVENT_ORIGINAL_CHANNEL_ID = "app_original_event_channel_id";

    public static Event eventOf(final Context context, final int channelId) {
        return new Event(context).channelId(channelId);
    }

    public static Event asyncEventOf(final Context context, final int channelId) {
        return new Event(context).async(true).channelId(channelId);
    }

    /**
     * Constructs an instance of the Event class with specified type, context, payload, and response listener.
     * This event object can be used to trigger specific actions or responses based on the event type and payload.
     *
     * @param context The {@link Context} in which the event is created and processed. It provides environmental data and configurations.
     */
    public Event(final Context context) {
        this.context = context;
        this.put("createdAt", System.currentTimeMillis());
    }

    /**
     * Returns the event name based on the channel ID.
     *
     * @return The name of the event.
     */
    public String channel() {
        return eventNameOf(channelId);
    }

    /**
     * Returns the event name based on the original channel ID.
     *
     * @return The name of the event.
     */
    public String nameOrg() {
        return eventNameOf(channelIdOrg());
    }

    public Nano nano() {
        return context.nano();
    }

    /**
     * @return The integer representing the type of the event. This typically corresponds to a specific kind of event.
     */
    public int channelId() {
        return channelId;
    }

    /**
     * @return The integer representing the original type of the event. This typically corresponds to a specific kind of event.
     */
    public int channelIdOrg() {
        return asIntOpt(EVENT_ORIGINAL_CHANNEL_ID).orElse(channelId);
    }

    /**
     * Sets the channel ID of the event.
     *
     * @param channelId The integer representing the type of the event. This typically corresponds to a specific kind of event.
     * @return self for chaining
     */
    public Event channelId(final int channelId) {
        this.channelId = channelId;
        return this;
    }

    /**
     * Sets the event to asynchronous mode, allowing the response to be handled by a listener.
     *
     * @return self for chaining
     */
    public Event async(final boolean async) {
        this.responseListener = async ? ignored -> {} : null;
        return this;
    }

    /**
     * Sets the event to asynchronous mode, allowing the response to be handled by a listener.
     *
     * @param responseListener A consumer that handles the response of the event processing. It can be used to execute actions based on the event's outcome or data.
     * @return self for chaining
     */
    public Event async(final Consumer<Object> responseListener) {
        this.responseListener = responseListener;
        return this;
    }

    /**
     * Sets the payload of the event.
     *
     * @param payload The data or object that is associated with this event. This can be any relevant information that needs to be passed along with the event.
     * @return self for chaining
     */
    public Event payload(final Supplier<Object> payload) {
        this.payload = payload;
        return this;
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

    public Object payload() {
        if (payloadRaw == null)
            payloadRaw = payload == null ? null : payload.get();
        return payloadRaw;
    }

    public boolean isBroadcast() {
        return asBooleanOpt("isBroadcast").orElse(false);
    }

    public Event broadcast(final boolean broadcast) {
        return putR("isBroadcast", broadcast);
    }

    public boolean isAcknowledged() {
        return response != null;
    }

    public Optional<Object> payloadOpt() {
        return ofNullable(payload());
    }

    public <T> T payload(final Class<T> type) {
        return convertObj(payload(), type);
    }

    public <T> Optional<T> payloadOpt(final Class<T> type) {
        return ofNullable(convertObj(payload(), type));
    }

    public Context context() {
        return context;
    }

    public boolean isAsync() {
        return responseListener != null;
    }

    public Consumer<Object> async() {
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

    @Override
    public Event putR(Object key, Object value) {
        this.put(key, value);
        return this;
    }

    public Type<Event> filter(final Predicate<Event> predicate) {
        return new Type<>(predicate.test(this) ? this : null);
    }

    public Throwable error() {
        return error;
    }

    public Event error(final Throwable error) {
        this.error = error;
        return this;
    }

    /**
     * Sends the event to the Nano instance for processing.
     *
     * @return self for chaining
     */
    public Event send() {
        nano().sendEvent(this);
        return this;
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("channel", channel())
            .putR("ack", response != null)
            .putR("listener", responseListener != null)
            .putR("payload", payload(String.class))
            .putR("size", context.size() + this.size()
                + (payload == null ? 0 : 1)
                + (responseListener == null ? 0 : 1)
                + (response == null ? 0 : 1)
                + (error == null ? 0 : 1)
            )
            .toJson();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Event event)) return false;
        if (!super.equals(o)) return false;
        return channelId == event.channelId && Objects.equals(context, event.context) && Objects.equals(responseListener, event.responseListener) && Objects.equals(payload, event.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), channelId, context, responseListener, payload);
    }
}
