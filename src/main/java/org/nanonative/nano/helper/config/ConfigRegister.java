package org.nanonative.nano.helper.config;

import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.model.Service;

import static java.util.Optional.ofNullable;

/**
 * Utility class for registering and retrieving configuration descriptions.
 * <p>
 * This class is typically used for {@link Service} or custom functions to display
 * configuration keys and descriptions when the Java property <code>-Dhelp=true</code> is set.
 * </p>
 */
public class ConfigRegister {

    /**
     * Registers a configuration key with its description.
     * <p>
     * If the key is valid and non-null, it will be standardized and added to the configuration keys map
     * with the provided description. If the description is null, an empty string will be used.
     * </p>
     *
     * @param key         the configuration key to register
     * @param description the description of the configuration key
     * @return the standardized key if registration is successful, or {@code null} if the key is invalid
     */
    public static String registerConfig(final String key, final String description) {
        return ofNullable(key)
            .filter(NanoUtils::hasText)
            .map(NanoBase::standardiseKey)
            .map(name -> {
                NanoBase.CONFIG_KEYS.computeIfAbsent(name, k -> description == null ? "" : description);
                return name;
            })
            .orElse(null);
    }

    /**
     * Retrieves the description of a registered configuration key.
     * <p>
     * If the key is valid and non-null, it will be standardized and its description
     * will be retrieved from the configuration keys map.
     * </p>
     *
     * @param key the configuration key whose description is to be retrieved
     * @return the description of the configuration key, or {@code null} if the key is invalid or not found
     */
    public static String configDescriptionOf(final String key) {
        return ofNullable(key)
            .filter(NanoUtils::hasText)
            .map(NanoBase::standardiseKey)
            .map(NanoBase.CONFIG_KEYS::get)
            .orElse(null);
    }

    private ConfigRegister() {
        // static util class
    }
}
