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

/**
 * Event container transporting payloads and metadata.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Event<C, R> extends TypeMap {

    protected final transient Channel<C, R> channel;
    protected final Context context;
    protected transient Consumer<R> responseListener;
    protected transient Supplier<C> payload;
    protected transient C payloadRaw;
    protected transient R response;
    protected Throwable error;
    protected final AtomicBoolean isAcknowledged = new AtomicBoolean(false);

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

    /**
     * Acknowledges the event and returns the resolved payload.
     * <p>
     * Equivalent to {@code acknowledge().payload()}.
     *
     * @return the payload value
     */
    public C payloadAck() {
        return acknowledge().payload();
    }

    /**
     * Returns the resolved payload if present.
     *
     * @return optional payload
     */
    public Optional<C> payloadOpt() {
        return ofNullable(payload());
    }

    /**
     * Acknowledges the event and returns the payload if present.
     * <p>
     * If the payload exists, {@link #respond(Object)} is invoked with {@code null} to mark acknowledgement and the
     * payload is returned.
     *
     * @return optional payload after acknowledgement
     */
    public Optional<C> payloadAckOpt() {
        return ofNullable(payload()).map(c -> {
            respond(null);
            return c;
        });
    }

    /**
     * Resolves the payload asynchronously on the {@link Context} executor and passes it to the consumer.
     * Does nothing if no payload has been set.
     *
     * @param consumer callback receiving the resolved payload
     * @return this event for chaining
     */
    public Event<C, R> payloadAsync(final Consumer<C> consumer) {
        if (payload != null)
            context.run(() -> consumer.accept(payload()));
        return this;
    }

    /**
     * Acknowledges first, then resolves the payload asynchronously and passes it to the consumer.
     *
     * @param consumer callback receiving the resolved payload
     * @return this event for chaining
     */
    public Event<C, R> payloadAckAsync(final Consumer<C> consumer) {
        acknowledge();
        return payloadAsync(consumer);
    }

    /**
     * Returns the payload, resolving the supplier on first access and caching the result.
     * <p>
     * If the resolved value is an {@code Event}, this instance records {@code containsEvent=true} and sets a
     * {@code parentEvent} link on the nested event to support acknowledgement and error propagation.
     *
     * @return the payload value; may be {@code null} if no supplier/value were provided
     */
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

    /**
     * Indicates whether the event has been acknowledged via {@link #respond(Object)} or {@link #acknowledge()}.
     *
     * @return {@code true} if acknowledged; otherwise {@code false}
     */
    public boolean isAcknowledged() {
        return isAcknowledged.getPlain();
    }

    /**
     * Marks the event as acknowledged without providing a response body.
     * <p>
     * Shorthand for {@code respond(null)}.
     *
     * @return this event for chaining
     */
    public Event<C, R> acknowledge() {
        return respond(null);
    }

    /**
     * Returns whether this event is marked as broadcast.
     * <p>
     * Broadcast is a lightweight flag stored in the event map; routing components may use it to fan out delivery.
     *
     * @return {@code true} if broadcast; otherwise {@code false}
     */
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public boolean isBroadcast() {
        return asBooleanOpt("isBroadcast").orElse(false);
    }

    /**
     * Sets the broadcast flag.
     *
     * @param broadcast whether the event should be treated as broadcast
     * @return this event for chaining
     */
    public Event<C, R> broadcast(final boolean broadcast) {
        put("isBroadcast", broadcast);
        return this;
    }

    /**
     * Returns the context associated with this event.
     *
     * @return the context
     */
    public Context context() {
        return context;
    }

    /**
     * Returns {@code true} if a response listener is installed (i.e., the event is asynchronous).
     *
     * @return {@code true} when async; otherwise {@code false}
     */
    public boolean isAsync() {
        return responseListener != null;
    }

    /**
     * Returns {@code true} if the resolved payload is itself an {@code Event}.
     *
     * @return whether the payload contains a nested event
     */
    public boolean containsEvent() {
        return this.asBooleanOpt("containsEvent").orElse(false);
    }

    /**
     * Sets the internal flag indicating that the payload contains a nested event.
     *
     * @param containsEvent {@code true} if payload is an event
     * @return this event for chaining
     */
    public Event<C, R> containsEvent(final boolean containsEvent) {
        return this.putR("containsEvent", containsEvent);
    }

    /**
     * Returns the currently configured response listener, if any.
     *
     * @return the listener or {@code null} if synchronous
     */
    public Consumer<R> listener() {
        return responseListener;
    }

    /**
     * Completes the event with a response and marks it as acknowledged.
     * <p>
     * If a listener is present, it is invoked with the response. Acknowledgement is also propagated to a parent or
     * nested event when applicable.
     *
     * @param response the response value (maybe {@code null})
     * @return this event for chaining
     */
    public Event<C, R> respond(final R response) {
        if (responseListener != null)
            responseListener.accept(response);
        this.response = response;
        if (!isAcknowledged())
            ofNullable(get("parentEvent")).filter(Event.class::isInstance).map(Event.class::cast).ifPresentOrElse(event -> event.isAcknowledged.set(true),
                    () -> Optional.of(containsEvent()).filter(containsEvent -> containsEvent).flatMap(containsEvent -> payloadOpt()).filter(Event.class::isInstance).map(Event.class::cast).ifPresent(event -> event.isAcknowledged.set(true)));
        this.isAcknowledged.set(true);
        return this;
    }

    /**
     * Returns the response value set via {@link #respond(Object)}.
     *
     * @return the response; may be {@code null} if none
     */
    public R response() {
        return response;
    }

    /**
     * Returns the response as an {@link Optional}.
     *
     * @return optional response
     */
    public Optional<R> responseOpt() {
        return ofNullable(response);
    }

    /**
     * Returns the response as an {@link Optional} only if it is compatible with {@code type}.
     *
     * @param type target class to check against the channel's response type
     * @param <T>  desired response view
     * @return optional typed response when assignable; otherwise empty
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> responseOpt(final Class<T> type) {
        return ofNullable(channel.response().isAssignableFrom(type) ? (T) response : null);
    }

    /**
     * Invokes the given consumer with this event and returns this instance.
     * <p>
     * Useful for debugging or fluent side-effects without breaking the chain.
     *
     * @param peek consumer observing this event
     * @return this event for chaining
     */
    public Event<C, R> peek(final Consumer<Event<C, R>> peek) {
        if (peek != null)
            peek.accept(this);
        return this;
    }

    /**
     * Puts the key/value into the underlying map and returns {@code this} for fluent chaining.
     *
     * @param key   map key
     * @param value map value
     * @return this event for chaining
     */
    @Override
    public Event<C, R> putR(final Object key, final Object value) {
        this.put(key, value);
        return this;
    }

    /**
     * Applies a predicate to this event and returns a {@link Type} wrapper containing either this event
     * (when the predicate passes) or {@code null}.
     * <p>
     * The {@code Type} class integrates nicely with the project’s {@code TypeMap} usage.
     *
     * @param predicate filter to evaluate
     * @return a {@code Type} containing this event or {@code null}
     */
    public Type<Event<C, R>> filter(final Predicate<Event<C, R>> predicate) {
        return new Type<>(predicate.test(this) ? this : null);
    }

    /**
     * Returns the most relevant error for this event.
     * <p>
     * If a direct error has been set via {@link #error(Throwable)}, it is returned. Otherwise, the method attempts to
     * retrieve an error from the parent event or a nested payload event (if present).
     *
     * @return the error or {@code null} if none found
     */
    public Throwable error() {
        return error == null
                ? ofNullable(get("parentEvent")).filter(Event.class::isInstance).map(Event.class::cast).map(Event::error)
                .or(() -> Optional.of(containsEvent()).filter(containsEvent -> containsEvent).flatMap(containsEvent -> payloadOpt()).filter(Event.class::isInstance).map(Event.class::cast).map(Event::error)).orElse(null)
                : error;
    }

    /**
     * Attaches an error to this event.
     *
     * @param error the throwable to associate
     * @return this event for chaining
     */
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

    /**
     * Returns a JSON-like representation with selected fields for diagnostics.
     */
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

    /**
     * Two events are equal when their underlying map content is equal and they share the same channel,
     * context, listener, and payload supplier.
     */
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
