package org.nanonative.nano.services.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// TODO: use TypeMap / Event itself

/**
 * Reusable record for file watch and unwatch operations.
 * Can be used for both single file and group-based watching.
 *
 * @param group Optional group name. If not provided, a default group will be used
 * @param paths List of file paths to watch/unwatch
 */
public record FileWatchRequest(
    String group,
    List<Path> paths
    ) {

    /**
     * Create a watch request for a single file with default group
     */
    public static FileWatchRequest forFile(final Path path) {
        return new FileWatchRequest(null, List.of(path));
    }

    /**
     * Create a watch request for multiple files with default group
     */
    public static FileWatchRequest forFiles(final List<Path> paths) {
        return new FileWatchRequest(null, paths);
    }

    /**
     * Create a watch request for a single file with specified group
     */
    public static FileWatchRequest forFileWithGroup(final String group, final Path path) {
        return new FileWatchRequest(group, List.of(path));
    }

    /**
     * Create a watch request for multiple files with specified group
     */
    public static FileWatchRequest forFilesWithGroup(final String group, final List<Path> paths)  {
        return new FileWatchRequest(group, paths);
    }

    /**
     * Get the group name or default group if none specified
     */
    public String getGroupOrDefault() {
        return group == null? "DEFAULT_GROUP" : group;
    }
}
