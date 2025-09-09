package org.nanonative.nano.helper.event.model;

import berlin.yuna.typemap.model.LinkedTypeMap;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.helper.NanoUtils;

import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.nanonative.nano.core.NanoBase.EVENT_CHANNELS;

public record Channel<T, R>(int id, String name, Class<T> payload, Class<R> response) {

    /**
     * Registers a new {@link Channel} with a given name if it does not already exist.
     * If the {@link Channel} payload already exists, it returns the existing {@link Channel}.
     *
     * @param name The name of the {@link Channel} payload to register.
     * @return The {@link Channel}  of the newly registered event payload, or the {@link Channel}  of the existing event payload if it already exists. Returns null if the input is null or empty.
     */
    public static Channel<Void, Void> registerChannelId(final String name) {
        return registerChannelId(name, Void.class, Void.class);
    }

    /**
     * Registers a new {@link Channel} with a given name if it does not already exist.
     * If the {@link Channel} payload already exists, it returns the existing {@link Channel}.
     *
     * @param name    The name of the {@link Channel} payload to register.
     * @param payload The class type of the payload for the {@link Channel}.
     * @return The {@link Channel}  of the newly registered event payload, or the {@link Channel}  of the existing event payload if it already exists. Returns null if the input is null or empty.
     */
    public static <C> Channel<C, Void> registerChannelId(final String name, final Class<C> payload) {
        return registerChannelId(name, payload, Void.class);
    }

    /**
     * Registers a new {@link Channel} with a given name if it does not already exist.
     * If the {@link Channel} payload already exists, it returns the existing {@link Channel}.
     *
     * @param name     The name of the {@link Channel} payload to register.
     * @param response The class type of the response for the {@link Channel}.
     * @return The {@link Channel}  of the newly registered event payload, or the {@link Channel}  of the existing event payload if it already exists. Returns null if the input is null or empty.
     */
    @SuppressWarnings("unchecked")
    public static <C, R> Channel<C, R> registerChannelId(final String name, final Class<C> type, final Class<R> response) {
        return (Channel<C, R>) ofNullable(name)
            .filter(NanoUtils::hasText)
            .map(nme -> channelOf(nme).orElseGet(() -> {
                final Channel<C, R> channel = new Channel<>(NanoBase.EVENT_ID_COUNTER.incrementAndGet(), nme, type, response);
                EVENT_CHANNELS.put(channel.id(), channel);
                return channel;
            }))
            .orElse(null);
    }

    /**
     * Attempts to find the {@link Channel} based on its name.
     * This method is primarily used for debugging or startup purposes and is not optimized for performance.
     *
     * @param name The name of the {@link Channel}.
     * @return An {@link Optional} containing the {@link Channel} of the {@link Event} payload if found, or empty if not found
     */
    public static Optional<Channel<?, ?>> channelOf(final String name) {
        return !NanoUtils.hasText(name) ? Optional.empty() : EVENT_CHANNELS.values().stream()
            .filter(channel -> channel.name().equals(name))
            .findFirst();
    }

    /**
     * Retrieves the name of an event {@link Channel} given its id.
     *
     * @param id for the {@link Channel}.
     * @return The name of the  {@link Channel} associated with the given id, or null if not found.
     */
    public static Channel<?, ?> channelOf(final int id) {
        return EVENT_CHANNELS.get(id);
    }


    /**
     * Checks if a {@link Channel} with the given id exists.
     *
     * @param id The id of the {@link Channel}  to check.
     * @return true if a {@link Channel} with the given id exists, false otherwise.
     */
    public static boolean isChannelIdAvailable(final int id) {
        return EVENT_CHANNELS.containsKey(id);
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("id", id)
            .putR("name", name)
            .putR("class", this.getClass().getSimpleName())
            .putR("type", payload.getSimpleName())
            .toJson();
    }
}
