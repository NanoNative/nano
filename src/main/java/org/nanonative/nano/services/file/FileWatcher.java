package org.nanonative.nano.services.file;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
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
import static org.nanonative.nano.helper.event.EventChannelRegister.registerChannelId;

public class FileWatcher extends Service {

    public static final int EVENT_WATCH_FILE = registerChannelId("WATCH_FILE");
    public static final int EVENT_UNWATCH_FILE = registerChannelId("UNWATCH_FILE");
    public static final int EVENT_FILE_CHANGE = registerChannelId("FILE_CHANGE");

    private WatchService watchService;
    private Map<WatchKey, Path> watchedFiles;
    private NanoThread watchThread;

    @Override
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedFiles = new ConcurrentHashMap<>();
            watchThread = context.runR(() -> {
                    while (watchService != null && watchedFiles != null) {
                        try {
                            final WatchKey key = watchService.take();

                            for (final WatchEvent<?> event : key.pollEvents()) {
                                final WatchEvent.Kind<?> kind = event.kind();
                                Path eventFile = (Path) event.context();
                                final Path fullPath = Optional.ofNullable(watchedFiles).map(map -> map.get(key)).orElse(null);
                                if (fullPath != null) {
                                    if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
                                        context.newEvent(EVENT_FILE_CHANGE).putR("kind", kind).payload(() -> fullPath).send();
                                    } else if (kind == ENTRY_DELETE) {
                                        watchedFiles.remove(key);
                                        key.cancel();
                                        context.newEvent(EVENT_FILE_CHANGE).putR("kind", kind).payload(() -> fullPath).send();
                                    }
                                }
                                key.reset();
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (final ClosedWatchServiceException ignored) {
                            // ignored on close
                        }
                    }
                }
            )[0];
        } catch (final Exception e) {
            context.error(() -> "Failed to initialize", e);
        }
    }

    @Override
    public void stop() {
        watchedFiles.keySet().forEach(WatchKey::cancel);
        watchedFiles.clear();
        try {
            if (watchService != null)
                watchService.close();
        } catch (Exception ignored) {
            // ignored
        }
        watchService = null;
        watchedFiles = null;
    }

    @Override
    public Object onFailure(final Event error) {
        return null;
    }

    @Override
    public void onEvent(final Event event) {
        event.ifPresentResp(EVENT_WATCH_FILE, Path.class, path -> {
            try {
                final WatchKey key = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                watchedFiles.put(key, path);
            } catch (final Exception e) {
                context.error(() -> "Failed to watch file", e);
            }
            return true;
        });
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {

    }
}
