package org.nanonative.nano.services.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// TODO: use TypeMap / Event itself

/**
 * Reusable record for file watch and unwatch operations.
 * Can be used for both single file and group-based watching.
 *
 * @param paths List of file paths to watch/unwatch
 * @param group Optional group name. If not provided, a default group will be used
 */
public record FileWatchRequest(
    List<Path> paths,
    Optional<String> group
) {

    /**
     * Create a watch request for a single file with default group
     */
    public static FileWatchRequest forFile(Path path) {
        return new FileWatchRequest(List.of(path), Optional.empty());
    }

    /**
     * Create a watch request for multiple files with default group
     */
    public static FileWatchRequest forFiles(List<Path> paths) {
        return new FileWatchRequest(paths, Optional.empty());
    }

    /**
     * Create a watch request for a single file with specified group
     */
    public static FileWatchRequest forFileWithGroup(Path path, String group) {
        return new FileWatchRequest(List.of(path), Optional.of(group));
    }

    /**
     * Create a watch request for multiple files with specified group
     */
    public static FileWatchRequest forFilesWithGroup(List<Path> paths, String group) {
        return new FileWatchRequest(paths, Optional.of(group));
    }

    /**
     * Get the group name or default group if none specified
     */
    public String getGroupOrDefault() {
        return group.orElse("DEFAULT_GROUP");
    }
}
