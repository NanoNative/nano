package org.nanonative.nano.services.file;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Optional;

/**
 * Record representing a file change event with path and WatchEvent kind.
 * Contains all information needed to understand what happened to which file.
 *
 * @param path  The path of the file that changed
 * @param kind  The type of change (ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
 * @param group Optional group name if this change was detected through group-based watching
 */
public record FileChangeEvent(
    Path path,
    WatchEvent.Kind<?> kind,
    Optional<String> group
) {

    /**
     * Create a file change event without group information
     */
    public static FileChangeEvent of(Path path, WatchEvent.Kind<?> kind) {
        return new FileChangeEvent(path, kind, Optional.empty());
    }

    /**
     * Create a file change event with group information
     */
    public static FileChangeEvent of(Path path, WatchEvent.Kind<?> kind, String group) {
        return new FileChangeEvent(path, kind, Optional.of(group));
    }

    /**
     * Get the kind name as a string for easier processing
     */
    public String getKindName() {
        return kind.name();
    }

    /**
     * Check if this is a creation event
     */
    public boolean isCreate() {
        return "ENTRY_CREATE".equals(getKindName());
    }

    /**
     * Check if this is a modification event
     */
    public boolean isModify() {
        return "ENTRY_MODIFY".equals(getKindName());
    }

    /**
     * Check if this is a deletion event
     */
    public boolean isDelete() {
        return "ENTRY_DELETE".equals(getKindName());
    }

    /**
     * Check if this event belongs to a specific group
     */
    public boolean belongsToGroup(String groupName) {
        return group.map(g -> g.equals(groupName)).orElse(false);
    }
}
