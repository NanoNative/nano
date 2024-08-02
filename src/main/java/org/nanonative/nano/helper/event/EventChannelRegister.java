package org.nanonative.nano.helper.event;

import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.core.NanoBase;

import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class EventChannelRegister {

    /**
     * Registers a new event type with a given name if it does not already exist.
     * If the event type already exists, it returns the existing event type's ID.
     *
     * @param channelName The name of the event type to register.
     * @return The ID of the newly registered event type, or the ID of the existing event type
     *         if it already exists. Returns -1 if the input is null or empty.
     */
    public static int registerChannelId(final String channelName) {
        return ofNullable(channelName).filter(NanoUtils::hasText).map(name -> eventIdOf(channelName).orElseGet(() -> {
            final int channelId = NanoBase.EVENT_ID_COUNTER.incrementAndGet();
            NanoBase.EVENT_TYPES.put(channelId, channelName);
            return channelId;
        })).orElse(-1);
    }

    /**
     * Retrieves the name of an event type given its ID.
     *
     * @param channelId The ID of the event type.
     * @return The name of the event type associated with the given ID, or null if not found.
     */
    public static String eventNameOf(final int channelId) {
        return NanoBase.EVENT_TYPES.get(channelId);
    }

    /**
     * Attempts to find the ID of an event type based on its name.
     * This method is primarily used for debugging or startup purposes and is not optimized for performance.
     *
     * @param channelName The name of the event type.
     * @return An {@link Optional} containing the ID of the event type if found, or empty if not found
     *         or if the input is null or empty.
     */
    public static Optional<Integer> eventIdOf(final String channelName) {
        return NanoUtils.hasText(channelName) ? NanoBase.EVENT_TYPES.entrySet().stream().filter(type -> type.getValue().equals(channelName)).map(Map.Entry::getKey).findFirst() : Optional.empty();
    }

    /**
     * Checks if an event type with the given ID exists.
     *
     * @param channelId The ID of the event type to check.
     * @return true if an event type with the given ID exists, false otherwise.
     */
    public static boolean isChannelIdAvailable(final int channelId) {
        return NanoBase.EVENT_TYPES.containsKey(channelId);
    }

    private EventChannelRegister() {
        // static util class
    }
}
