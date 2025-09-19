package org.nanonative.nano.services.file;

import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.event.model.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_UNWATCH_FILE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_WATCH_FILE;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

class FileWatcherTest {

    @Test
    void shouldDetectFileCreation() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-create");
        final List<Event<Path, Void>> events = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> events.add(event));
            ctx.newEvent(EVENT_WATCH_FILE, () -> tempDir).send();

            final Path file = tempDir.resolve("create.txt");
            Files.createFile(file);

            assertTrue(waitForEvent(events, "ENTRY_CREATE", file));
        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldDetectFileModification() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-modify");
        final List<Event<Path, Void>> events = new CopyOnWriteArrayList<>();
        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> events.add(event));

            final Path file = Files.createFile(tempDir.resolve("modify.txt"));
            ctx.newEvent(EVENT_WATCH_FILE, () -> tempDir).send();

            Files.writeString(file, "new content");

            assertTrue(waitForEvent(events, "ENTRY_MODIFY", file));
        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldDetectFileDeletion() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-delete");
        final List<Event<Path, Void>> events = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> events.add(event));

            final Path file = Files.createFile(tempDir.resolve("delete.txt"));
            ctx.newEvent(EVENT_WATCH_FILE, () -> tempDir).send();

            Files.delete(file);

            assertTrue(waitForEvent(events, "ENTRY_DELETE", file));
        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldUnwatchFileSuccessfully() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-unwatch");
        final List<Event<Path, Void>> events = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> events.add(event));

            final Path file = Files.createFile(tempDir.resolve("unwatch.txt"));
            ctx.newEvent(EVENT_WATCH_FILE, () -> tempDir).send();
            ctx.newEvent(EVENT_UNWATCH_FILE, () -> tempDir).send();

            Files.delete(file);
            TimeUnit.MILLISECONDS.sleep(300); // Allow time for potential unwanted event

            assertFalse(eventOccurred(events, file));
        } finally {
            cleanup(tempDir);
        }
    }

    private boolean waitForEvent(List<Event<Path, Void>> events, String kind, Path path) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (events.stream().anyMatch(e ->
                kind.equals(e.get("kind")) &&
                    e.payloadOpt().map(p -> p.endsWith(path.getFileName())).orElse(false))) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return false;
    }

    private boolean eventOccurred(final List<Event<Path, Void>> events, Path path) {
        return events.stream()
            .anyMatch(e -> e.payloadOpt()
                .map(p -> p.endsWith(path.getFileName()))
                .orElse(false));
    }

    private void cleanup(final Path dir) throws IOException {
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(p -> {
                try {Files.deleteIfExists(p);} catch (IOException ignored) {}
            });
    }
}
