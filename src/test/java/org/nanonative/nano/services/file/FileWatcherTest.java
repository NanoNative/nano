package org.nanonative.nano.services.file;

import berlin.yuna.typemap.model.TypeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.event.model.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.CONFIG_CHANGE_GROUP;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_UNWATCH_FILE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_UNWATCH_GROUP;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_WATCH_FILE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_WATCH_GROUP;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Execution(ExecutionMode.SAME_THREAD)

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

    private boolean eventOccurred(final List<Event<Path, Void>> events, Path path) {
        return events.stream()
            .anyMatch(e -> e.payloadOpt()
                .map(p -> p.endsWith(path.getFileName()))
                .orElse(false));
    }

    // New Group-Based Tests

    @Test
    void shouldWatchGroupSuccessfully() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-group");
        final List<Event<Path, Void>> events = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
                if ("TEST_GROUP".equals(event.get("groupKey"))) {
                    events.add(event);
                }
            });

            // Register group to watch the temp directory
            final TypeMap groupData = new TypeMap()
                .putR("groupKey", "TEST_GROUP")
                .putR("paths", List.of(tempDir.toString()));

            ctx.newEvent(EVENT_WATCH_GROUP, () -> groupData).send();

            // Create a file in the watched directory
            final Path file = tempDir.resolve("group-test.txt");
            Files.createFile(file);

            assertTrue(waitForEvent(events, "ENTRY_CREATE", file));
        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldTriggerConfigChangeOnApplicationPropertiesModification() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-config");
        final List<Event<?, ?>> configChangeEvents = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());

            // Listen for config change events
            nano.subscribeEvent(EVENT_CONFIG_CHANGE, event -> configChangeEvents.add(event));

            // Register config watching group
            final TypeMap configGroupData = new TypeMap()
                .putR("groupKey", CONFIG_CHANGE_GROUP)
                .putR("paths", List.of(tempDir.toString()));

            ctx.newEvent(EVENT_WATCH_GROUP, () -> configGroupData).send();

            // Create application.properties file
            final Path configFile = tempDir.resolve("application.properties");
            Files.writeString(configFile, "test.key=initial_value\nother.key=other_value\n");

            // Modify the config file
            Thread.sleep(100); // Ensure file system notices the change
            Files.writeString(configFile, "test.key=updated_value\nother.key=other_value\nnew.key=new_value\n");

            // Wait for config change event
            assertTrue(waitForConfigChangeEvent(configChangeEvents));

            // Verify the event contains the expected config changes
            final Event<?, ?> configEvent = configChangeEvents.get(0);
            final Map<String, Object> changes = (Map<String, Object>) configEvent.payload();
            assertThat(changes).containsKey("test.key");
            assertThat(changes.get("test.key")).isEqualTo("updated_value");
            assertThat(changes).containsKey("new.key");
            assertThat(changes.get("new.key")).isEqualTo("new_value");

        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldHandleMultipleGroupsConcurrently() throws Exception {
        final Path tempDir1 = Files.createTempDirectory("file-watcher-multi1");
        final Path tempDir2 = Files.createTempDirectory("file-watcher-multi2");
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger group1Events = new AtomicInteger(0);
        final AtomicInteger group2Events = new AtomicInteger(0);

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());

            // Subscribe to file changes with group-specific handling
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
                final String groupKey = event.asString("groupKey");
                if ("GROUP1".equals(groupKey)) {
                    group1Events.incrementAndGet();
                    latch.countDown();
                } else if ("GROUP2".equals(groupKey)) {
                    group2Events.incrementAndGet();
                    latch.countDown();
                }
            });

            // Register two groups watching different directories
            final TypeMap group1Data = new TypeMap()
                .putR("groupKey", "GROUP1")
                .putR("paths", List.of(tempDir1.toString()));

            final TypeMap group2Data = new TypeMap()
                .putR("groupKey", "GROUP2")
                .putR("paths", List.of(tempDir2.toString()));

            ctx.newEvent(EVENT_WATCH_GROUP, () -> group1Data).send();
            ctx.newEvent(EVENT_WATCH_GROUP, () -> group2Data).send();

            // Create files in both directories simultaneously
            Files.createFile(tempDir1.resolve("file1.txt"));
            Files.createFile(tempDir2.resolve("file2.txt"));

            // Wait for both events
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertThat(group1Events.get()).isEqualTo(1);
            assertThat(group2Events.get()).isEqualTo(1);

        } finally {
            cleanup(tempDir1);
            cleanup(tempDir2);
        }
    }

    @Test
    void shouldUnwatchGroupWithoutAffectingOtherGroups() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-unwatch-group");
        final List<Event<Path, Void>> group1Events = new CopyOnWriteArrayList<>();
        final List<Event<Path, Void>> group2Events = new CopyOnWriteArrayList<>();

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());

            // Subscribe to file changes for both groups
            nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
                final String groupKey = event.asString("groupKey");
                if ("GROUP1".equals(groupKey)) {
                    group1Events.add(event);
                } else if ("GROUP2".equals(groupKey)) {
                    group2Events.add(event);
                }
            });

            // Register both groups to watch the same directory
            final TypeMap group1Data = new TypeMap()
                .putR("groupKey", "GROUP1")
                .putR("paths", List.of(tempDir.toString()));

            final TypeMap group2Data = new TypeMap()
                .putR("groupKey", "GROUP2")
                .putR("paths", List.of(tempDir.toString()));

            ctx.newEvent(EVENT_WATCH_GROUP, () -> group1Data).send();
            ctx.newEvent(EVENT_WATCH_GROUP, () -> group2Data).send();

            // Create a file - both groups should receive events
            final Path file1 = tempDir.resolve("test1.txt");
            Files.createFile(file1);

            assertTrue(waitForEvent(group1Events, "ENTRY_CREATE", file1));
            assertTrue(waitForEvent(group2Events, "ENTRY_CREATE", file1));

            // Unwatch GROUP1 only
            ctx.newEvent(EVENT_UNWATCH_GROUP, () -> "GROUP1").send();
            Thread.sleep(100); // Allow unwatch to complete

            // Clear events and create another file
            group1Events.clear();
            group2Events.clear();

            final Path file2 = tempDir.resolve("test2.txt");
            Files.createFile(file2);

            // Only GROUP2 should receive the event now
            assertFalse(waitForEvent(group1Events, "ENTRY_CREATE", file2, 1000)); // Shorter timeout
            assertTrue(waitForEvent(group2Events, "ENTRY_CREATE", file2));

        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldPreventDuplicateWatchingOfSamePath() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-duplicate");

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());

            // Get reference to FileWatcher service
            final FileWatcher fileWatcher = nano.services().stream()
                .filter(FileWatcher.class::isInstance)
                .map(FileWatcher.class::cast)
                .findFirst()
                .orElseThrow();

            // Register the same path multiple times under different groups
            final TypeMap group1Data = new TypeMap()
                .putR("groupKey", "DUPLICATE_GROUP1")
                .putR("paths", List.of(tempDir.toString()));

            final TypeMap group2Data = new TypeMap()
                .putR("groupKey", "DUPLICATE_GROUP2")
                .putR("paths", List.of(tempDir.toString()));

            ctx.newEvent(EVENT_WATCH_GROUP, () -> group1Data).send();
            ctx.newEvent(EVENT_WATCH_GROUP, () -> group2Data).send();

            // Verify both groups are watching the path
            final Set<String> watchedGroups = fileWatcher.getWatchedGroups();
            assertThat(watchedGroups).contains("DUPLICATE_GROUP1", "DUPLICATE_GROUP2");

            final Set<Path> group1Paths = fileWatcher.getGroupPaths("DUPLICATE_GROUP1");
            final Set<Path> group2Paths = fileWatcher.getGroupPaths("DUPLICATE_GROUP2");

            assertThat(group1Paths).hasSize(1);
            assertThat(group2Paths).hasSize(1);
            assertThat(group1Paths.iterator().next().toString()).endsWith(tempDir.getFileName().toString());
            assertThat(group2Paths.iterator().next().toString()).endsWith(tempDir.getFileName().toString());

        } finally {
            cleanup(tempDir);
        }
    }

    @Test
    void shouldHandleConcurrentGroupOperations() throws Exception {
        final Path tempDir = Files.createTempDirectory("file-watcher-concurrent");
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(10);
        final AtomicInteger successCount = new AtomicInteger(0);

        try {
            final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
            final Context ctx = nano.context(getClass());

            // Launch multiple threads to register/unregister groups concurrently
            for (int i = 0; i < 10; i++) {
                final int threadId = i;
                new Thread(() -> {
                    try {
                        startLatch.await();

                        final TypeMap groupData = new TypeMap()
                            .putR("groupKey", "CONCURRENT_GROUP_" + threadId)
                            .putR("paths", List.of(tempDir.toString()));

                        // Register group
                        ctx.newEvent(EVENT_WATCH_GROUP, () -> groupData).send();
                        Thread.sleep(100);

                        // Unregister group
                        ctx.newEvent(EVENT_UNWATCH_GROUP, () -> "CONCURRENT_GROUP_" + threadId).send();

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        completeLatch.countDown();
                    }
                }).start();
            }

            // Start all threads
            startLatch.countDown();

            // Wait for completion
            assertTrue(completeLatch.await(10, TimeUnit.SECONDS));
            assertThat(successCount.get()).isEqualTo(10);

        } finally {
            cleanup(tempDir);
        }
    }

    private boolean waitForConfigChangeEvent(List<Event<?, ?>> events) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (!events.isEmpty()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return false;
    }

    private boolean waitForEvent(List<Event<Path, Void>> events, String kind, Path path) throws InterruptedException {
        return waitForEvent(events, kind, path, 5000);
    }

    private boolean waitForEvent(List<Event<Path, Void>> events, String kind, Path path, long timeoutMs) throws InterruptedException {
        final long sleepInterval = 50;
        final int maxTries = (int) (timeoutMs / sleepInterval);

        for (int i = 0; i < maxTries; i++) {
            if (events.stream().anyMatch(e ->
                kind.equals(e.get("kind")) &&
                    e.payloadOpt().map(p -> p.endsWith(path.getFileName())).orElse(false))) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(sleepInterval);
        }
        return false;
    }

    private void cleanup(final Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
}
