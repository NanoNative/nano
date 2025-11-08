package org.nanonative.nano.services.file;

import berlin.yuna.typemap.model.LinkedTypeMap;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;

@SuppressWarnings({"java:S135"}) // too many break statements
public class FileWatcher extends Service {

    // API
    public static final Channel<FileWatchRequest, Void> EVENT_FILE_WATCH = registerChannelId("WATCH", FileWatchRequest.class);
    public static final Channel<FileWatchRequest, Void> EVENT_FILE_UNWATCH = registerChannelId("UNWATCH", FileWatchRequest.class);
    public static final Channel<FileChangeEvent, Void> EVENT_FILE_CHANGE = registerChannelId("FILE_CHANGE", FileChangeEvent.class);

    // Watcher
    protected final AtomicReference<WatchService> watchService = new AtomicReference<>();

    // Directories <-> keys
    protected final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
    protected final Map<Path, WatchKey> dirToKey = new ConcurrentHashMap<>();

    // Groups
    protected static final class GroupState {
        final Set<Path> dirs = ConcurrentHashMap.newKeySet(); // watched directories
        final Set<Path> files = ConcurrentHashMap.newKeySet(); // exact files (absolute); empty → whole dirs
    }

    protected final Map<String, GroupState> groups = new ConcurrentHashMap<>();

    // Reverse lookup: dir → groups watching it
    protected final Map<Path, Set<String>> dirToGroups = new ConcurrentHashMap<>();

    @Override
    public void start() {
        try {
            final WatchService ws = FileSystems.getDefault().newWatchService();
            if (watchService.compareAndSet(null, ws))
                context.run(() -> watchQueue(ws));
        } catch (Exception e) {
            context.error(e, () -> "Failed to initialize " + getClass().getSimpleName());
        }
    }

    @Override
    public void stop() {
        // Cancel keys first (cheap, non-blocking)
        dirToKey.values().forEach(WatchKey::cancel);
        keyToDir.clear();
        dirToKey.clear();

        // Close the WatchService
        final WatchService ws = watchService.getAndSet(null);
        if (ws != null) {
            try {ws.close();} catch (Exception ignored) {}
        }

        // Clear group state
        groups.clear();
        dirToGroups.clear();
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {return null;}

    @Override
    public void onEvent(final Event<?, ?> event) {
        event.channel(EVENT_FILE_WATCH).map(Event::payload).ifPresent(this::onWatch);
        event.channel(EVENT_FILE_UNWATCH).map(Event::payload).ifPresent(this::onUnwatch);
    }

    @SuppressWarnings({"java:S135"}) // too many break warnings
    protected void watchQueue(final WatchService ws) {
        while (true) {
            try {
                final WatchKey key = ws.take();
                final Path watchedDir = keyToDir.get(key);
                if (watchedDir != null) {
                    receiveEvents(key, watchedDir);
                }
                // If reset fails, the key is dead; clean it.
                if (!key.reset()) {
                    final Path dead = keyToDir.remove(key);
                    if (dead != null) dirToKey.remove(dead);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException closed) {
                break;
            }
        }
    }

    protected void receiveEvents(final WatchKey key, final Path watchedDir) {
        for (WatchEvent<?> ev : key.pollEvents()) {
            final WatchEvent.Kind<?> kind = ev.kind();
            if (kind == OVERFLOW) continue;

            final Path rel = (Path) ev.context();
            if (rel == null) continue;

            final Path eventPath = watchedDir.resolve(rel).toAbsolutePath().normalize();
            dispatch(eventPath, kind);

            // watched dir deleted → unwatch
            if (kind == ENTRY_DELETE && Objects.equals(eventPath, watchedDir)) {
                unwatchDir(watchedDir);
            }
        }
    }

    /* ===== watch/unwatch ===== */

    protected void onWatch(final FileWatchRequest req) {
        final String group = req.getGroupOrDefault();
        final GroupState gs = groups.computeIfAbsent(group, k -> new GroupState());

        for (Path p : req.paths()) {
            if (p == null) continue;
            final Path abs = p.toAbsolutePath().normalize();

            if (Files.isDirectory(abs)) {
                // directory: register as-is
                if (watchDir(abs)) {
                    gs.dirs.add(abs);
                    dirToGroups.computeIfAbsent(abs, path -> ConcurrentHashMap.newKeySet()).add(group);
                }
                continue;
            }

            // file: watch parent directory, add file to allowlist
            final Path parent = abs.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                if (watchDir(parent)) {
                    gs.dirs.add(parent);
                    dirToGroups.computeIfAbsent(parent, path -> ConcurrentHashMap.newKeySet()).add(group);
                }
                gs.files.add(abs);
            }
        }

        context.debug(() -> "Group [{}]: watching {} dir(s), {} file(s)", group, gs.dirs.size(), gs.files.size());
    }

    protected void onUnwatch(final FileWatchRequest req) {
        final String group = req.getGroupOrDefault();
        final GroupState gs = groups.remove(group);
        if (gs == null) return;

        // Drop reverse links; unwatch dirs that no one else needs
        for (Path dir : gs.dirs) {
            final Set<String> watchers = dirToGroups.get(dir);
            if (watchers != null) {
                watchers.remove(group);
                if (watchers.isEmpty()) {
                    dirToGroups.remove(dir);
                    unwatchDir(dir);
                }
            }
        }
    }

    protected boolean watchDir(final Path dir) {
        final Path abs = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) return false;
        if (dirToKey.containsKey(abs)) return true;

        try {
            final WatchService ws = watchService.get();
            if (ws == null) {
                context.warn(() -> "Watch request arrived before service start [{}]", abs);
                return false;
            }
            final WatchKey key = abs.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            keyToDir.put(key, abs);
            dirToKey.put(abs, key);
            return true;
        } catch (Exception e) {
            context.error(e, () -> "Failed to watch dir [{}]", abs);
            return false;
        }
    }

    protected void unwatchDir(final Path dir) {
        final Path abs = dir.toAbsolutePath().normalize();
        final WatchKey key = dirToKey.remove(abs);
        if (key != null) {
            key.cancel();
            keyToDir.remove(key);
        }
    }

    /* ===== dispatch ===== */

    protected void dispatch(final Path eventPath, final WatchEvent.Kind<?> kind) {
        // 1) generic event
        final FileChangeEvent base = FileChangeEvent.of(eventPath, kind);
        context.newEvent(EVENT_FILE_CHANGE).payload(() -> base).send();

        // 2) group-scoped events
        final Path parent = Optional.ofNullable(eventPath.getParent()).orElse(eventPath);
        final Set<String> gs = dirToGroups.get(parent);
        if (gs == null || gs.isEmpty()) return;

        for (String g : gs) {
            final GroupState s = groups.get(g);
            if (s == null) continue;
            // allowlist empty -> whole dir; otherwise only selected files
            if (s.files.isEmpty() || s.files.contains(eventPath)) {
                final FileChangeEvent ge = FileChangeEvent.of(eventPath, kind, g);
                context.newEvent(EVENT_FILE_CHANGE).payload(() -> ge).broadcast(true).send();
            }
        }
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) { /* hooks later */ }

    @Override
    public String toString() {
        final int dirCount = groups.values().stream().mapToInt(s -> s.dirs.size()).sum();
        final int fileCount = groups.values().stream().mapToInt(s -> s.files.size()).sum();
        return new LinkedTypeMap()
            .putR("watchedKeys", keyToDir.size())
            .putR("pathToKey", dirToKey.size())
            .putR("groups", groups.size())
            .putR("groupDirs", dirCount)
            .putR("groupFiles", fileCount)
            .putR("class", getClass().getSimpleName())
            .toJson();
    }
}
