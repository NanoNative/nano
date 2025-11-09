package org.nanonative.nano.services.file;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.testutil.TestFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.core.config.TestConfig.TEST_LOG_LEVEL;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_UNWATCH;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_WATCH;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Execution(ExecutionMode.CONCURRENT)
class FileWatcherTest {

    private static final long DEFAULT_TIMEOUT_MS = Long.parseLong(System.getProperty("test.file.watcher.timeout.ms", "2100"));
    private static final long SHORT_TIMEOUT_MS = Long.parseLong(System.getProperty("test.file.watcher.short.timeout.ms", "300"));

    private static Path classRoot;

    @BeforeAll
    static void initRoot() throws IOException {
        classRoot = Files.createTempDirectory("file-watcher-suite");
    }

    @AfterAll
    static void cleanupRoot() throws IOException {
        TestFiles.deleteTree(classRoot);
    }

    @Test
    void shouldEmitLifecycleEvents() throws Exception {
        final Path dir = newTempDir("file-watcher-lifecycle");
        final Path file = dir.resolve("sample.txt");
        final BlockingQueue<FileChangeEvent> changes = new LinkedBlockingQueue<>();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        nano.subscribeEvent(EVENT_FILE_CHANGE, event -> changes.offer(event.payload()));
        ctx.newEvent(EVENT_FILE_WATCH, () -> FileWatchRequest.forFile(dir)).send();

        Files.writeString(file, "created", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.isCreate() && ev.path().endsWith(file.getFileName()))).isPresent();

        Files.writeString(file, "modified", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.isModify() && ev.path().endsWith(file.getFileName()))).isPresent();

        Files.deleteIfExists(file);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.isDelete() && ev.path().endsWith(file.getFileName()))).isPresent();

        stopNano(nano, ctx);
    }

    @Test
    void shouldEmitGroupScopedEvents() throws Exception {
        final Path dir = newTempDir("file-watcher-group");
        final Path file = dir.resolve("grouped.txt");
        final BlockingQueue<FileChangeEvent> changes = new LinkedBlockingQueue<>();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        nano.subscribeEvent(EVENT_FILE_CHANGE, event -> changes.offer(event.payload()));
        ctx.newEvent(EVENT_FILE_WATCH, () ->
            FileWatchRequest.forFilesWithGroup("TEST_GROUP", List.of(dir))
        ).send();

        Files.writeString(file, "created", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.isCreate() && ev.belongsToGroup("TEST_GROUP") && ev.path().endsWith(file.getFileName())
        )).isPresent();

        stopNano(nano, ctx);
    }

    @Test
    void shouldStopEmittingEventsAfterUnwatch() throws Exception {
        final Path dir = newTempDir("file-watcher-unwatch");
        final Path file = dir.resolve("watch-me.txt");
        final BlockingQueue<FileChangeEvent> changes = new LinkedBlockingQueue<>();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        nano.subscribeEvent(EVENT_FILE_CHANGE, event -> changes.offer(event.payload()));

        ctx.newEvent(EVENT_FILE_WATCH, () ->
            FileWatchRequest.forFilesWithGroup("OFF", List.of(dir))
        ).send();

        Files.writeString(file, "one", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("OFF") && ev.path().endsWith(file.getFileName()))).isPresent();
        changes.clear();

        ctx.newEvent(EVENT_FILE_UNWATCH, () ->
            FileWatchRequest.forFilesWithGroup("OFF", List.of(dir))
        ).send();

        Files.writeString(file, "second", UTF_8);
        assertThat(pollMatching(changes, SHORT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("OFF"))).isEmpty();

        stopNano(nano, ctx);
    }

    @Test
    void shouldKeepOtherGroupsActiveWhenOneIsUnwatched() throws Exception {
        final Path dir = newTempDir("file-watcher-multi");
        final BlockingQueue<FileChangeEvent> changes = new LinkedBlockingQueue<>();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        nano.subscribeEvent(EVENT_FILE_CHANGE, event -> changes.offer(event.payload()));

        ctx.newEvent(EVENT_FILE_WATCH, () ->
            FileWatchRequest.forFilesWithGroup("GROUP1", List.of(dir))
        ).send();
        ctx.newEvent(EVENT_FILE_WATCH, () ->
            FileWatchRequest.forFilesWithGroup("GROUP2", List.of(dir))
        ).send();

        Files.writeString(dir.resolve("first.txt"), "first", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("GROUP1") && ev.isCreate())).isPresent();
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("GROUP2") && ev.isCreate())).isPresent();
        changes.clear();

        ctx.newEvent(EVENT_FILE_UNWATCH, () ->
            FileWatchRequest.forFilesWithGroup("GROUP1", List.of(dir))
        ).send();

        Files.writeString(dir.resolve("second.txt"), "second", UTF_8);
        assertThat(pollMatching(changes, DEFAULT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("GROUP2") && ev.isCreate())).isPresent();
        assertThat(pollMatching(changes, SHORT_TIMEOUT_MS,
            ev -> ev.belongsToGroup("GROUP1"))).isEmpty();

        stopNano(nano, ctx);
    }

    @Test
    void shouldEmitEventsForMultipleDirectories() throws Exception {
        final Path dirOne = newTempDir("file-watcher-multi-dir-1");
        final Path dirTwo = newTempDir("file-watcher-multi-dir-2");
        final Path fileOne = dirOne.resolve("one.txt");
        final Path fileTwo = dirTwo.resolve("two.txt");
        final BlockingQueue<FileChangeEvent> events = new LinkedBlockingQueue<>();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        nano.subscribeEvent(EVENT_FILE_CHANGE, event -> events.offer(event.payload()));

        ctx.newEvent(EVENT_FILE_WATCH, () ->
            FileWatchRequest.forFilesWithGroup("DUO", List.of(dirOne, dirTwo))
        ).send();

        Files.writeString(fileOne, "one", UTF_8);
        assertThat(pollMatching(events, DEFAULT_TIMEOUT_MS,
            ev -> ev.isCreate() && ev.belongsToGroup("DUO") && ev.path().endsWith(fileOne.getFileName()))).isPresent();

        Files.writeString(fileTwo, "two", UTF_8);
        assertThat(pollMatching(events, DEFAULT_TIMEOUT_MS,
            ev -> ev.isCreate() && ev.belongsToGroup("DUO") && ev.path().endsWith(fileTwo.getFileName()))).isPresent();

        stopNano(nano, ctx);
    }

    @Test
    void shouldHandleConcurrentGroupOperations() throws Exception {
        final Path dir = newTempDir("file-watcher-concurrent");
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(10);
        final AtomicInteger success = new AtomicInteger();

        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, TEST_LOG_LEVEL), new FileWatcher());
        final Context ctx = nano.context(getClass());
        for (int i = 0; i < 10; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    start.await();
                    final String group = "CONCURRENT_" + index;
                    ctx.newEvent(EVENT_FILE_WATCH, () ->
                        FileWatchRequest.forFilesWithGroup(group, List.of(dir))
                    ).send();
                    ctx.newEvent(EVENT_FILE_UNWATCH, () ->
                        FileWatchRequest.forFilesWithGroup(group, List.of(dir))
                    ).send();
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // intentionally swallowed for test stability
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertThat(done.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(success.get()).isEqualTo(10);

        stopNano(nano, ctx);
    }

    @Test
    void shouldDescribeFileChangeEventMethods() {
        final Path testPath = Path.of("/tmp/test.txt");

        final FileChangeEvent createEvent = FileChangeEvent.of(testPath, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
        assertThat(createEvent.path()).isEqualTo(testPath);
        assertThat(createEvent.getKindName()).isEqualTo("ENTRY_CREATE");
        assertThat(createEvent.isCreate()).isTrue();
        assertThat(createEvent.isModify()).isFalse();
        assertThat(createEvent.isDelete()).isFalse();
        assertThat(createEvent.belongsToGroup("ANY_GROUP")).isFalse();
        assertThat(createEvent.group()).isEmpty();

        final FileChangeEvent modifyEvent = FileChangeEvent.of(testPath, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
        assertThat(modifyEvent.isCreate()).isFalse();
        assertThat(modifyEvent.isModify()).isTrue();
        assertThat(modifyEvent.isDelete()).isFalse();

        final FileChangeEvent deleteEvent = FileChangeEvent.of(testPath, java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
        assertThat(deleteEvent.isCreate()).isFalse();
        assertThat(deleteEvent.isModify()).isFalse();
        assertThat(deleteEvent.isDelete()).isTrue();

        final FileChangeEvent groupEvent = FileChangeEvent.of(testPath, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE, "TEST_GROUP");
        assertThat(groupEvent.belongsToGroup("TEST_GROUP")).isTrue();
        assertThat(groupEvent.belongsToGroup("OTHER_GROUP")).isFalse();
        assertThat(groupEvent.group()).contains("TEST_GROUP");
    }

    @Test
    void shouldDescribeFileWatchRequestFactories() {
        final Path path1 = Path.of("/tmp/path1");
        final Path path2 = Path.of("/tmp/path2");
        final List<Path> paths = List.of(path1, path2);

        final FileWatchRequest singleFileRequest = FileWatchRequest.forFile(path1);
        assertThat(singleFileRequest.paths()).hasSize(1);
        assertThat(singleFileRequest.paths().getFirst()).isEqualTo(path1);
        assertThat(singleFileRequest.group()).isNull();
        assertThat(singleFileRequest.getGroupOrDefault()).isEqualTo("DEFAULT_GROUP");

        final FileWatchRequest multiFileRequest = FileWatchRequest.forFiles(paths);
        assertThat(multiFileRequest.paths()).containsExactly(path1, path2);
        assertThat(multiFileRequest.group()).isNull();
        assertThat(multiFileRequest.getGroupOrDefault()).isEqualTo("DEFAULT_GROUP");

        final FileWatchRequest singleFileGroupRequest = FileWatchRequest.forFileWithGroup("TEST_GROUP", path1);
        assertThat(singleFileGroupRequest.paths()).containsExactly(path1);
        assertThat(singleFileGroupRequest.group()).contains("TEST_GROUP");
        assertThat(singleFileGroupRequest.getGroupOrDefault()).isEqualTo("TEST_GROUP");

        final FileWatchRequest multiFileGroupRequest = FileWatchRequest.forFilesWithGroup("MULTI_GROUP", paths);
        assertThat(multiFileGroupRequest.paths()).containsExactly(path1, path2);
        assertThat(multiFileGroupRequest.group()).contains("MULTI_GROUP");
        assertThat(multiFileGroupRequest.getGroupOrDefault()).isEqualTo("MULTI_GROUP");
    }

    /* ===== helpers ===== */

    private static Optional<FileChangeEvent> pollMatching(
        final BlockingQueue<FileChangeEvent> queue,
        final long timeoutMs,
        final Predicate<FileChangeEvent> predicate
    ) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        final List<FileChangeEvent> buffer = new ArrayList<>();
        while (System.currentTimeMillis() <= deadline) {
            final long remaining = deadline - System.currentTimeMillis();
            final FileChangeEvent event = queue.poll(Math.max(1L, remaining), TimeUnit.MILLISECONDS);
            if (event == null) {
                break;
            }
            if (predicate.test(event)) {
                buffer.forEach(queue::offer);
                return Optional.of(event);
            }
            buffer.add(event);
        }
        buffer.forEach(queue::offer);
        return Optional.empty();
    }

    private Path newTempDir(final String prefix) throws IOException {
        return Files.createTempDirectory(classRoot, prefix);
    }

    private static void stopNano(final Nano nano, final Context ctx) {
        nano.stop(ctx).waitForStop();
    }
}
