package org.nanonative.nano.services.file;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;

public class FileWatcher extends Service {

    public static final String CONFIG_CHANGE_GROUP = "CONFIG_CHANGE";

    public static final Channel<Path, Void> EVENT_WATCH_FILE = registerChannelId("WATCH_FILE", Path.class);
    public static final Channel<Path, Void> EVENT_UNWATCH_FILE = registerChannelId("UNWATCH_FILE", Path.class);
    public static final Channel<Path, Void> EVENT_FILE_CHANGE = registerChannelId("FILE_CHANGE", Path.class);

    // New group-based events
    public static final Channel<TypeMap, Void> EVENT_WATCH_GROUP = registerChannelId("WATCH_GROUP", TypeMap.class);
    public static final Channel<String, Void> EVENT_UNWATCH_GROUP = registerChannelId("UNWATCH_GROUP", String.class);

    private WatchService watchService;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Core watching data structures
    private Map<WatchKey, Path> watchedKeys;
    private Map<Path, WatchKey> pathToKey;

    // Group-based watching structures
    private Map<String, Set<Path>> groupToPaths;           // groupKey -> Set<Path>
    private Map<Path, Set<String>> pathToGroups;          // path -> Set<groupKey>
    private Map<String, Map<String, Object>> groupMetadata; // groupKey -> metadata

    @Override
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedKeys = new ConcurrentHashMap<>();
            pathToKey = new ConcurrentHashMap<>();
            groupToPaths = new ConcurrentHashMap<>();
            pathToGroups = new ConcurrentHashMap<>();
            groupMetadata = new ConcurrentHashMap<>();

            // Initialize default config watching for known config directories
            initializeConfigWatching();

            context.run(() -> {
                while (watchService != null) {
                    try {
                        final WatchKey key = watchService.take();
                        final Path watchedDir = watchedKeys.get(key);

                        if (watchedDir != null) {
                            for (final WatchEvent<?> event : key.pollEvents()) {
                                final WatchEvent.Kind<?> kind = event.kind();
                                if (kind == OVERFLOW) continue;

                                final Path eventPath = watchedDir.resolve((Path) event.context());
                                processFileEvent(eventPath, kind);

                                if (kind == ENTRY_DELETE && pathToKey.containsKey(eventPath)) {
                                    unwatch(eventPath);
                                }
                            }
                        }
                        key.reset();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final ClosedWatchServiceException ignored) {
                        // Watch service has been closed, exit the loop
                    }
                }
            });

        } catch (Exception e) {
            context.error(e, () -> "Failed to initialize " + this.getClass().getSimpleName());
        }
    }

    @Override
    public void stop() {
        Optional.ofNullable(watchedKeys).ifPresent(map -> {
            map.keySet().forEach(WatchKey::cancel);
            map.clear();
        });
        Optional.ofNullable(pathToKey).ifPresent(Map::clear);

        try {
            if (watchService != null)
                watchService.close();
        } catch (Exception ignored) {
            // ignored
        }
        watchService = null;
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return null;
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        // Legacy single file watching
        event.channel(EVENT_WATCH_FILE).map(Event::payload).ifPresent(this::watch);
        event.channel(EVENT_UNWATCH_FILE).map(Event::payload).ifPresent(this::unwatch);

        // New group-based watching
        event.channel(EVENT_WATCH_GROUP).map(Event::payload).ifPresent(this::watchGroup);
        event.channel(EVENT_UNWATCH_GROUP).map(Event::payload).ifPresent(this::unwatchGroup);
    }

    /**
     * Initialize watching for config directories automatically
     */
    private void initializeConfigWatching() {
        final TypeMap configGroupData = new TypeMap()
            .putR("groupKey", CONFIG_CHANGE_GROUP)
            .putR("paths", List.of(".", "config", ".config", "resources", ".resources", "resources/config", ".resources/config"));

        watchGroup(configGroupData);
        context.debug(() -> "Initialized config file watching for group [{}]", CONFIG_CHANGE_GROUP);
    }

    /**
     * Process file events and trigger appropriate group-specific events
     */
    private void processFileEvent(final Path eventPath, final WatchEvent.Kind<?> kind) {
        lock.readLock().lock();
        try {
            // Send generic file change event (legacy behavior)
            context.newEvent(EVENT_FILE_CHANGE)
                .putR("kind", kind.name())
                .payload(() -> eventPath)
                .send();

            // Check if this path is watched by any groups
            final Set<String> groups = pathToGroups.get(eventPath.getParent());
            if (groups != null && !groups.isEmpty()) {
                for (final String groupKey : groups) {
                    processGroupFileEvent(groupKey, eventPath, kind);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Process file events for a specific group
     */
    private void processGroupFileEvent(final String groupKey, final Path eventPath, final WatchEvent.Kind<?> kind) {
        // Special handling for CONFIG_CHANGE group
        if (CONFIG_CHANGE_GROUP.equals(groupKey) && isConfigFile(eventPath)) {
            context.debug(() -> "Config file changed: [{}] kind [{}]", eventPath, kind.name());

            // Trigger EVENT_CONFIG_CHANGE with updated config
            try {
                // Re-read the affected config file and send the changes
                final Map<String, Object> configChanges = readConfigFileChanges(eventPath);
                if (!configChanges.isEmpty()) {
                    context.newEvent(EVENT_CONFIG_CHANGE, () -> configChanges)
                        .broadcast(true) // Ensure all listeners receive it
                        .async(true)
                        .send();
                    context.info(() -> "Triggered EVENT_CONFIG_CHANGE for file [{}]", eventPath);
                }
            } catch (Exception e) {
                context.error(e, () -> "Failed to process config file change [{}]", eventPath);
            }
        }

        // Send group-specific file change event
        context.newEvent(EVENT_FILE_CHANGE)
            .putR("kind", kind.name())
            .putR("groupKey", groupKey)
            .payload(() -> eventPath)
            .broadcast(true)
            .send();
    }

    /**
     * Check if a file is a config file
     */
    private boolean isConfigFile(final Path path) {
        final String fileName = path.getFileName().toString().toLowerCase();
        return fileName.startsWith("application") && fileName.endsWith(".properties");
    }

    /**
     * Read config file changes and return as map
     */
    private Map<String, Object> readConfigFileChanges(final Path configFile) {
        final Map<String, Object> changes = new ConcurrentHashMap<>();

        if (!Files.exists(configFile)) {
            return changes;
        }

        try {
            Files.lines(configFile)
                .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                .forEach(line -> {
                    final int equalIndex = line.indexOf('=');
                    if (equalIndex > 0) {
                        final String key = line.substring(0, equalIndex).trim();
                        final String value = line.substring(equalIndex + 1).trim();
                        changes.put(key, value);

                        // Also add normalized versions
                        changes.put(key.replace(".", "_"), value);
                        changes.put(key.replace(".", "_").toUpperCase(), value);
                    }
                });
        } catch (Exception e) {
            context.warn(e, () -> "Failed to read config file [{}]", configFile);
        }

        return changes;
    }

    /**
     * Watch files/directories for a specific group
     */
    public void watchGroup(final TypeMap groupData) {
        final String groupKey = groupData.asString("groupKey");
        final List<String> pathStrings = groupData.asList(String.class, "paths");

        if (groupKey == null || pathStrings == null || pathStrings.isEmpty()) {
            context.warn(() -> "Invalid group data for watching: {}", groupData);
            return;
        }

        lock.writeLock().lock();
        try {
            final Set<Path> groupPaths = ConcurrentHashMap.newKeySet();
            final Map<String, Object> metadata = new ConcurrentHashMap<>();
            groupData.forEach((k, v) -> metadata.put(String.valueOf(k), v));

            for (final String pathString : pathStrings) {
                final Path path = Path.of(pathString).toAbsolutePath().normalize();

                // Only watch directories that exist
                if (Files.exists(path) && Files.isDirectory(path)) {
                    if (watch(path)) {
                        groupPaths.add(path);
                        pathToGroups.computeIfAbsent(path, k -> ConcurrentHashMap.newKeySet()).add(groupKey);
                    }
                }
            }

            if (!groupPaths.isEmpty()) {
                groupToPaths.put(groupKey, groupPaths);
                groupMetadata.put(groupKey, metadata);
                context.debug(() -> "Registered group [{}] watching {} paths", groupKey, groupPaths.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stop watching files for a specific group
     */
    public void unwatchGroup(final String groupKey) {
        if (groupKey == null) return;

        lock.writeLock().lock();
        try {
            final Set<Path> groupPaths = groupToPaths.remove(groupKey);
            groupMetadata.remove(groupKey);

            if (groupPaths != null) {
                for (final Path path : groupPaths) {
                    final Set<String> groups = pathToGroups.get(path);
                    if (groups != null) {
                        groups.remove(groupKey);
                        // If no other groups are watching this path, unwatch it
                        if (groups.isEmpty()) {
                            pathToGroups.remove(path);
                            unwatch(path);
                        }
                    }
                }
                context.debug(() -> "Unregistered group [{}] that was watching {} paths", groupKey, groupPaths.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Legacy method - watch a single path
     */
    public boolean watch(final Path path) {
        try {
            if (pathToKey.containsKey(path)) return true;

            final WatchKey key = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchedKeys.put(key, path);
            pathToKey.put(path, key);
            return true;
        } catch (Exception e) {
            context.error(e, () -> "Failed to watch path [{}]", path);
            return false;
        }
    }

    /**
     * Legacy method - unwatch a single path
     */
    public void unwatch(final Path path) {
        final WatchKey key = pathToKey.remove(path);
        if (key != null) {
            key.cancel();
            watchedKeys.remove(key);
        }
    }

    /**
     * Get all groups currently being watched
     */
    public Set<String> getWatchedGroups() {
        lock.readLock().lock();
        try {
            return Set.copyOf(groupToPaths.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get paths watched by a specific group
     */
    public Set<Path> getGroupPaths(final String groupKey) {
        lock.readLock().lock();
        try {
            final Set<Path> paths = groupToPaths.get(groupKey);
            return paths != null ? Set.copyOf(paths) : Set.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        // Could add configurable parameters for file watching behavior
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return new LinkedTypeMap()
                .putR("watchedKeys", watchedKeys.size())
                .putR("pathToKey", pathToKey.size())
                .putR("groups", groupToPaths.size())
                .putR("groupPaths", groupToPaths.values().stream().mapToInt(Set::size).sum())
                .putR("class", this.getClass().getSimpleName())
                .toJson();
        } finally {
            lock.readLock().unlock();
        }
    }
}
