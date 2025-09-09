package org.nanonative.nano.helper.event.model;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.Type;
import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.nano.core.model.Context;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

//@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Event<C, R> extends TypeMap {

    protected final Channel<C, R> channel;
    protected final Context context;
    protected transient Consumer<R> responseListener;
    protected transient Supplier<C> payload;
    protected transient C payloadRaw;
    protected transient R response;
    protected Throwable error;
    protected final AtomicBoolean isAcknowledged = new AtomicBoolean(false);

    public static <C, R> Event<C, R> eventOf(final Context context, final Channel<C, R> channel) {
        return new Event<>(context, channel);
    }

    /**
     * Constructs an instance of the Event class with specified payload, context, payload, and response listener.
     * This event object can be used to trigger specific actions or responses based on the event payload and payload.
     *
     * @param context The {@link Context} in which the event is created and processed. It provides environmental data and configurations.
     * @param channel The {@link Channel} associated with this event, defining the type of payload and response.
     */
    public Event(final Context context, final Channel<C, R> channel) {
        this.context = context;
        this.channel = channel;
        this.put("createdAt", System.nanoTime());
    }

    /**
     * Returns the name of the {@link Channel}.
     *
     * @return the name of the {@link Channel}.
     */
    public Channel<C, R> channel() {
        return channel;
    }

    /**
     * Returns the event as an {@link Optional} of type {@link Event}.
     * This method allows for safe retrieval of the event, ensuring that it matches the specified channel.
     *
     * @param channel The channel to match against the event's channel.
     * @return An {@link Optional} containing the event if it matches the channel, otherwise an empty {@link Optional}.
     */
    @SuppressWarnings({"unchecked", "java:S3358"})
    public <A, B> Optional<Event<A, B>> channel(final Channel<A, B> channel) {
        return this.channel.id() == channel.id()
            ? Optional.of((Event<A, B>) this)
            : containsEvent() ? ((Event<?, ?>) payload()).channel(channel) : Optional.empty();
    }

    /**
     * Sets the event to asynchronous mode, allowing the response to be handled by a listener.
     *
     * @return self for chaining
     */
    public Event<C, R> async(final boolean async) {
        this.responseListener = async ? ignored -> {} : null;
        return this;
    }

    /**
     * Sets the event to asynchronous mode, allowing the response to be handled by a listener.
     *
     * @param responseListener A consumer that handles the response of the event processing. It can be used to execute actions based on the event's outcome or data.
     * @return self for chaining
     */
    public Event<C, R> async(final Consumer<R> responseListener) {
        this.responseListener = responseListener;
        return this;
    }

    /**
     * Sets the payload of the event.
     *
     * @param payload The data or object that is associated with this event. This can be any relevant information that needs to be passed along with the event.
     * @return self for chaining
     */
    public Event<C, R> payload(final Supplier<C> payload) {
        this.payload = payload;
        return this;
    }

    public C payloadAck() {
        return acknowledge().payload();
    }

    public Optional<C> payloadOpt() {
        return ofNullable(payload());
    }

    public Optional<C> payloadAckOpt() {
        return ofNullable(payload()).map(c -> {
            respond(null);
            return c;
        });
    }

    public Event<C, R> payloadAsync(final Consumer<C> consumer) {
        if (payload != null)
            context.run(() -> consumer.accept(payload()));
        return this;
    }

    public Event<C, R> payloadAckAsync(final Consumer<C> consumer) {
        acknowledge();
        return payloadAsync(consumer);
    }

    public C payload() {
        if (payloadRaw == null && payload != null) {
            payloadRaw = payload.get();
            if (payloadRaw instanceof Event) {
                containsEvent(true);
                ((Event<?, ?>) payloadRaw).put("parentEvent", this);
            }
        }
        return payloadRaw;
    }

    public boolean isAcknowledged() {
        return isAcknowledged.getPlain();
    }

    public Event<C, R> acknowledge() {
        return respond(null);
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public boolean isBroadcast() {
        return asBooleanOpt("isBroadcast").orElse(false);
    }

    public Event<C, R> broadcast(final boolean broadcast) {
        put("isBroadcast", broadcast);
        return this;
    }

    public Context context() {
        return context;
    }

    public boolean isAsync() {
        return responseListener != null;
    }

    public boolean containsEvent() {
        return this.asBooleanOpt("containsEvent").orElse(false);
    }

    public Event<C, R> containsEvent(final boolean containsEvent) {
        return this.putR("containsEvent", containsEvent);
    }

    public Consumer<R> listener() {
        return responseListener;
    }

    public Event<C, R> respond(final R response) {
        if (responseListener != null)
            responseListener.accept(response);
        this.response = response;
        if (!isAcknowledged())
            ofNullable(get("parentEvent")).filter(Event.class::isInstance).map(Event.class::cast).ifPresentOrElse(event -> event.isAcknowledged.set(true),
                () -> Optional.of(containsEvent()).filter(containsEvent -> containsEvent).flatMap(containsEvent -> payloadOpt()).filter(Event.class::isInstance).map(Event.class::cast).ifPresent(event -> event.isAcknowledged.set(true)));;
        this.isAcknowledged.set(true);
        return this;
    }

    public R response() {
        return response;
    }

    public Optional<R> responseOpt() {
        return ofNullable(response);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> responseOpt(final Class<T> type) {
        return ofNullable(channel.response().isAssignableFrom(type) ? (T) response : null);
    }

    public Event<C, R> peek(final Consumer<Event<C, R>> peek) {
        if (peek != null)
            peek.accept(this);
        return this;
    }

    @Override
    public Event<C, R> putR(final Object key, final Object value) {
        this.put(key, value);
        return this;
    }

    public Type<Event<C, R>> filter(final Predicate<Event<C, R>> predicate) {
        return new Type<>(predicate.test(this) ? this : null);
    }

    public Throwable error() {
        return error == null
            ? ofNullable(get("parentEvent")).filter(Event.class::isInstance).map(Event.class::cast).map(Event::error)
            .or(() -> Optional.of(containsEvent()).filter(containsEvent -> containsEvent).flatMap(containsEvent -> payloadOpt()).filter(Event.class::isInstance).map(Event.class::cast).map(Event::error)).orElse(null)
            : error;
    }

    public Event<C, R> error(final Throwable error) {
        this.error = error;
        return this;
    }

    /**
     * Sends the event to the Nano instance for processing.
     *
     * @return self for chaining
     */
    public Event<C, R> send() {
        context.nano().sendEvent(this);
        return this;
    }

    /**
     * Sends the event to the Nano instance for processing.
     *
     * @return {@link Context} for chaining
     */
    public Context sendR() {
        context.nano().sendEvent(this);
        return this.context;
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("channel", channel())
            .putR("ack", response != null)
            .putR("listener", responseListener != null)
            .putR("payload", ofNullable(payload()).map(Object::toString).orElse(null))
            .putR("class", this.getClass().getSimpleName())
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
        if (!(o instanceof Event<?, ?> event)) return false;
        if (!super.equals(o)) return false;
        return channel == event.channel && Objects.equals(context, event.context) && Objects.equals(responseListener, event.responseListener) && Objects.equals(payload, event.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), channel, context, responseListener, payload);
    }
}
