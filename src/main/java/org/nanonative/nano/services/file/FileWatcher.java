package org.nanonative.nano.services.file;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;

public class FileWatcher extends Service {

    public static final Channel<Path, Void> EVENT_WATCH_FILE = registerChannelId("WATCH_FILE", Path.class);
    public static final Channel<Path, Void>  EVENT_UNWATCH_FILE = registerChannelId("UNWATCH_FILE", Path.class);
    public static final Channel<Path, Void>  EVENT_FILE_CHANGE = registerChannelId("FILE_CHANGE", Path.class);

    private WatchService watchService;
    private Map<WatchKey, Path> watchedKeys;
    private Map<Path, WatchKey> pathToKey;

    @Override
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedKeys = new ConcurrentHashMap<>();
            pathToKey = new ConcurrentHashMap<>();

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
                                context.newEvent(EVENT_FILE_CHANGE)
                                    .putR("kind", kind.name())
                                    .payload(() -> eventPath)
                                    .send();

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
            context.error(() -> "Failed to initialize " + this.getClass().getSimpleName(), e);
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
    public void onEvent(final Event<?, ?>  event) {
        event.channel(EVENT_WATCH_FILE).map(Event::payloadAck).ifPresent(this::watch);
        event.channel(EVENT_UNWATCH_FILE).map(Event::payloadAck).ifPresent(this::unwatch);
    }

    public boolean watch(final Path path) {
        try {
            if (pathToKey.containsKey(path)) return true;

            final WatchKey key = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchedKeys.put(key, path);
            pathToKey.put(path, key);
            return true;
        } catch (Exception e) {
            context.error(e, () -> "Failed to watch path [%s]", path);
            return false;
        }
    }

    public void unwatch(final Path path) {
        final WatchKey key = pathToKey.remove(path);
        if (key != null) {
            key.cancel();
            watchedKeys.remove(key);
        }
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        // No configurable parameters… yet.
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("watchedKeys", watchedKeys.size())
            .putR("pathToKey", pathToKey.size())
            .putR("class", this.getClass().getSimpleName())
            .toJson();
    }
}
