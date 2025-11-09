package org.nanonative.nano.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight filesystem helpers for tests.
 */
public final class TestFiles {

    private TestFiles() {
        // no instances
    }

    public static void deleteTree(final Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths
                .sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort for tests
                    }
                });
        }
    }
}
