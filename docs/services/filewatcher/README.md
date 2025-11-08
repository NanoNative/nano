# FileWatcher Service

> **Enhanced Universal File System Monitoring with Automatic Config Reloading**

The FileWatcher service provides group-based file system monitoring capabilities with automatic EVENT_CONFIG_CHANGE triggering for configuration files. It's designed to be generic, reusable, and highly concurrent.

## Key Features

### 🔄 **Automatic Config Reloading**
- Automatically triggers `EVENT_CONFIG_CHANGE` when `application.properties` files are modified
- Seamless integration with Nano's configuration system
- No manual configuration required - works out of the box

### 👥 **Group-Based Watching**
- Register arbitrary file/directory sets under named groups using `FileWatchRequest`
- Prevent duplicate system watches even when multiple groups share directories
- Remove a group without disturbing other watchers on the same path

### 🔒 **Thread-Safe Concurrency**
- Backed by lock-free concurrent collections
- Safe concurrent group registration/unregistration
- Handles overlapping group definitions correctly

### 🚀 **High Performance**
- Efficient duplicate prevention
- Minimal memory footprint
- Leverages Java NIO WatchService for native OS file system events

## Quick Start

### Basic Usage

```java
final Nano nano = new Nano(new FileWatcher());

nano.subscribeEvent(EVENT_FILE_CHANGE, event -> event.payloadOpt().ifPresent(change -> {
    System.out.println(change.getKindName() + " " + change.path());
}));
```

### Group-Based Watching

```java
final List<Path> certDirs = List.of(Path.of("/etc/ssl/certs"), Path.of("/opt/app/certificates"));
final FileWatchRequest request = FileWatchRequest.forFilesWithGroup(certDirs, "SSL_CERTIFICATES");

nano.context().newEvent(EVENT_FILE_WATCH, () -> request).send();

nano.subscribeEvent(EVENT_FILE_CHANGE, event -> event.payloadOpt()
    .filter(change -> change.belongsToGroup("SSL_CERTIFICATES"))
    .ifPresent(change -> reloadCertificates(change.path())));
```

## API Reference

### Events

| Event                | Payload            | Description                                         |
|----------------------|--------------------|-----------------------------------------------------|
| `EVENT_FILE_WATCH`   | `FileWatchRequest` | Register files/directories under a group key        |
| `EVENT_FILE_UNWATCH` | `FileWatchRequest` | Unregister a group (paths optional when removing)   |
| `EVENT_FILE_CHANGE`  | `FileChangeEvent`  | File system change detected (kind + group metadata) |

### Observability

```java
// Listen for change notifications
nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
    event.payloadOpt().ifPresent(change -> {
        if (change.belongsToGroup("CONFIG_CHANGE")) {
            System.out.println("Config updated: " + change.path());
        }
    });
});
```

## Configuration Integration

### Automatic Config Watching

FileWatcher automatically monitors these directories for `application*.properties` files:
- `.` (current directory)
- `config/`
- `.config/`
- `resources/`
- `.resources/`
- `resources/config/`
- `.resources/config/`

When config files change, `EVENT_CONFIG_CHANGE` is automatically triggered with the parsed configuration changes.

### Config Change Event Structure

```java
nano.subscribeEvent(EVENT_CONFIG_CHANGE, event -> {
    final Map<String, Object> changes = event.payload();

    // Original keys
    changes.get("server.port");     // "8080"

    // Normalized keys (automatic)
    changes.get("server_port");     // "8080"
    changes.get("SERVER_PORT");     // "8080"
});
```

## Advanced Usage

### HttpServer Certificate Reloading

```java
public class HttpsService {

    public void start() {
        final FileWatchRequest certGroup = FileWatchRequest.forFilesWithGroup(
            List.of(Path.of("/etc/ssl/private"), Path.of("/etc/ssl/certs")),
            "HTTPS_CERTS"
        );

        nano.context().newEvent(EVENT_FILE_WATCH, () -> certGroup).send();

        // React to certificate changes
        nano.subscribeEvent(EVENT_FILE_CHANGE, this::handleCertChange);
    }

    private void handleCertChange(Event<FileChangeEvent, Void> event) {
        event.payloadOpt()
            .filter(change -> change.belongsToGroup("HTTPS_CERTS"))
            .filter(FileChangeEvent::isModify)
            .filter(change -> change.path().toString().endsWith(".pem"))
            .ifPresent(change -> reloadSslContext(change.path()));
    }
}
```

### Multi-Environment Config Watching

```java
// Development: Watch local config files
if (isDevelopment()) {
    final FileWatchRequest devConfigGroup = FileWatchRequest.forFilesWithGroup(
        List.of(Path.of("./config"), Path.of("./local-config")),
        "DEV_CONFIG"
    );

    context.newEvent(EVENT_FILE_WATCH, () -> devConfigGroup).send();
}

// Production: Watch mounted config volumes
if (isProduction()) {
    final FileWatchRequest prodConfigGroup = FileWatchRequest.forFilesWithGroup(
        List.of(Path.of("/opt/app/config"), Path.of("/mnt/config-volume")),
        "PROD_CONFIG"
    );

    context.newEvent(EVENT_FILE_WATCH, () -> prodConfigGroup).send();
}
```

### Cleanup on Shutdown

```java
@Override
public void stop() {
    // Unregister groups on shutdown
    context.newEvent(EVENT_FILE_UNWATCH, () -> FileWatchRequest.forFilesWithGroup(List.of(), "MY_GROUP")).send();
    context.newEvent(EVENT_FILE_UNWATCH, () -> FileWatchRequest.forFilesWithGroup(List.of(), "SSL_CERTIFICATES")).send();
}
```

## Best Practices

### ✅ **Recommended Patterns**

```java
// Use descriptive group keys
final String GROUP_KEY = "HTTP_CLIENT_CERTIFICATES";

FileWatchRequest.forFilesWithGroup(List.of(Path.of("/etc/ssl/certs")), GROUP_KEY);  // ✅ Directory

// Handle all change types appropriately
    nano.subscribeEvent(EVENT_FILE_CHANGE, event -> event.payloadOpt().ifPresent(change -> {
        if (change.isCreate()) handleFileCreated(change.path());
        else if (change.isModify()) handleFileModified(change.path());
        else if (change.isDelete()) handleFileDeleted(change.path());
    }));

// Use broadcast for config changes (following best practices)
context.newEvent(EVENT_CONFIG_CHANGE, () -> configChanges)
    .broadcast(true) // Ensure all listeners receive
    .send();
```

### ❌ **Anti-Patterns**

```java
// Don't watch individual files if you can avoid it
FileWatchRequest.forFiles(List.of(Path.of("/path/file1.txt"), Path.of("/path/file2.txt"))); // ❌ prefer directory

// Don't create groups for single-use scenarios
final String GROUP_KEY = "TEMP_GROUP_" + UUID.randomUUID(); // ❌

// Don't forget to unregister groups on shutdown
// Missing cleanup can cause resource leaks // ❌
```

## Concurrency & Performance

### Thread Safety
- **Read Operations**: Multiple threads can safely read group information simultaneously
- **Write Operations**: Group registration/unregistration uses concurrent maps for atomicity
- **File Events**: Processed sequentially per directory, concurrent across directories

### Performance Characteristics
- **Memory**: O(G + P) where G = groups, P = watched paths
- **CPU**: Minimal overhead - uses native OS file system events
- **Scalability**: Handles hundreds of groups and thousands of files efficiently

### Concurrent Group Operations

```java
// Safe to call from multiple threads simultaneously
CompletableFuture.allOf(
    CompletableFuture.runAsync(() -> registerGroup("GROUP_A", paths1)),
    CompletableFuture.runAsync(() -> registerGroup("GROUP_B", paths2)),
    CompletableFuture.runAsync(() -> registerGroup("GROUP_C", paths3))
).join();
```

## Integration Examples

### Spring-Style Configuration Reloading

```java
@Component
public class ConfigReloadHandler {

    @EventListener(EVENT_CONFIG_CHANGE)
    public void handleConfigChange(Event<Map<String, Object>, Void> event) {
        final Map<String, Object> changes = event.payload();

        // Update datasource if database config changed
        if (changes.containsKey("spring.datasource.url")) {
            refreshDataSource(changes);
        }

        // Update logging levels
        if (changes.containsKey("logging.level.root")) {
            updateLoggingConfig(changes);
        }
    }
}
```

### Kubernetes ConfigMap Integration

```java
public class K8sConfigWatcher {

    public void watchConfigMaps() {
        final FileWatchRequest k8sGroup = FileWatchRequest.forFilesWithGroup(
            List.of(
                Path.of("/etc/config"),   // ConfigMap mount
                Path.of("/etc/secrets"),  // Secret mount
                Path.of("/opt/app/config")
            ),
            "K8S_CONFIGMAPS"
        );

        context.newEvent(EVENT_FILE_WATCH, () -> k8sGroup).send();
    }
}
```

## Troubleshooting

### Common Issues

**FileWatcher not detecting changes:**
```bash
# Check if directory exists and is readable
ls -la /path/to/watched/directory

# Verify file system supports inotify (Linux)
cat /proc/sys/fs/inotify/max_user_watches
```

**CONFIG_CHANGE events not firing:**
- Ensure files are named `application*.properties`
- Check that directories being watched exist
- Verify FileWatcher service is started

### Debug Logging

```java
// Enable debug logging to see FileWatcher activity
final Nano nano = new Nano(Map.of(
    CONFIG_LOG_LEVEL, DEBUG
), new FileWatcher());
```

---

The enhanced FileWatcher service provides robust, production-ready file system monitoring with automatic configuration reloading, making it ideal for cloud-native applications that need to respond to configuration changes without restarts.
### FileWatchRequest Helpers

```java
// Single directory, default group
FileWatchRequest.forFile(Path.of("./config"));

// Multiple directories with explicit group
FileWatchRequest.forFilesWithGroup(List.of(Path.of("./config"), Path.of("./secrets")), "CONFIGS");

// Later removal
nano.context().newEvent(EVENT_FILE_UNWATCH, () -> FileWatchRequest.forFilesWithGroup(List.of(), "CONFIGS")).send();
```
