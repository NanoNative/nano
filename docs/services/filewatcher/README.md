# FileWatcher Service

> **Enhanced Universal File System Monitoring with Automatic Config Reloading**

The FileWatcher service provides group-based file system monitoring capabilities with automatic EVENT_CONFIG_CHANGE triggering for configuration files. It's designed to be generic, reusable, and highly concurrent.

## Key Features

### 🔄 **Automatic Config Reloading**
- Automatically triggers `EVENT_CONFIG_CHANGE` when `application.properties` files are modified
- Seamless integration with Nano's configuration system
- No manual configuration required - works out of the box

### 👥 **Group-Based Watching**
- Multiple components can register file paths under different group keys
- Prevents duplicate watching of the same files/folders
- Clean group removal without affecting other groups watching same files

### 🔒 **Thread-Safe Concurrency**
- Built with `ReentrantReadWriteLock` for optimal concurrent performance
- Safe concurrent group registration/unregistration
- Handles overlapping groups correctly

### 🚀 **High Performance**
- Efficient duplicate prevention
- Minimal memory footprint
- Leverages Java NIO WatchService for native OS file system events

## Quick Start

### Basic Usage

```java
// FileWatcher starts automatically and watches config directories
final Nano nano = new Nano(new FileWatcher());

// Listen for any file changes
nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
    final Path changedFile = event.payload();
    final String changeType = event.asString("kind"); // ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
    System.out.println("File " + changeType + ": " + changedFile);
});

// Listen for config changes (automatic)
nano.subscribeEvent(EVENT_CONFIG_CHANGE, event -> {
    final Map<String, Object> configChanges = event.payload();
    System.out.println("Config updated: " + configChanges);
});
```

### Group-Based Watching

```java
// Register a group to watch specific directories
final TypeMap certificateWatchGroup = new TypeMap()
    .putR("groupKey", "SSL_CERTIFICATES")
    .putR("paths", List.of("/etc/ssl/certs", "/opt/app/certificates"));

context.newEvent(EVENT_WATCH_GROUP, () -> certificateWatchGroup).send();

// Listen for certificate changes
nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
    if ("SSL_CERTIFICATES".equals(event.get("groupKey"))) {
        final Path certFile = event.payload();
        // Reload SSL certificates
        reloadCertificates(certFile);
    }
});
```

## API Reference

### Events

| Event | Payload | Description |
|-------|---------|-------------|
| `EVENT_WATCH_GROUP` | `TypeMap` | Register files/directories under a group key |
| `EVENT_UNWATCH_GROUP` | `String` | Unregister an entire group |
| `EVENT_FILE_CHANGE` | `Path` | File system change detected |
| `EVENT_WATCH_FILE` | `Path` | Legacy: Watch single file/directory |
| `EVENT_UNWATCH_FILE` | `Path` | Legacy: Unwatch single file/directory |

### Group Registration

```java
final TypeMap groupData = new TypeMap()
    .putR("groupKey", "MY_GROUP")           // Required: Unique group identifier
    .putR("paths", List.of("/path1", "/path2")); // Required: Paths to watch

context.newEvent(EVENT_WATCH_GROUP, () -> groupData).send();
```

### Public Methods

```java
public Set<String> getWatchedGroups()           // Get all active group keys
public Set<Path> getGroupPaths(String groupKey) // Get paths watched by a group
public void watchGroup(TypeMap groupData)       // Register group (event-driven)
public void unwatchGroup(String groupKey)       // Unregister group (event-driven)
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
        // Watch certificate directory
        final TypeMap certGroup = new TypeMap()
            .putR("groupKey", "HTTPS_CERTS")
            .putR("paths", List.of("/etc/ssl/private", "/etc/ssl/certs"));

        nano.context().newEvent(EVENT_WATCH_GROUP, () -> certGroup).send();

        // React to certificate changes
        nano.subscribeEvent(EVENT_FILE_CHANGE, this::handleCertChange);
    }

    private void handleCertChange(Event<Path, Void> event) {
        if ("HTTPS_CERTS".equals(event.get("groupKey"))) {
            final Path certFile = event.payload();
            final String kind = event.asString("kind");

            if ("ENTRY_MODIFY".equals(kind) && certFile.toString().endsWith(".pem")) {
                // Reload SSL context
                reloadSslContext(certFile);
            }
        }
    }
}
```

### Multi-Environment Config Watching

```java
// Development: Watch local config files
if (isDevelopment()) {
    final TypeMap devConfigGroup = new TypeMap()
        .putR("groupKey", "DEV_CONFIG")
        .putR("paths", List.of("./config", "./local-config"));

    context.newEvent(EVENT_WATCH_GROUP, () -> devConfigGroup).send();
}

// Production: Watch mounted config volumes
if (isProduction()) {
    final TypeMap prodConfigGroup = new TypeMap()
        .putR("groupKey", "PROD_CONFIG")
        .putR("paths", List.of("/opt/app/config", "/mnt/config-volume"));

    context.newEvent(EVENT_WATCH_GROUP, () -> prodConfigGroup).send();
}
```

### Cleanup on Shutdown

```java
@Override
public void stop() {
    // Unregister groups on shutdown
    context.newEvent(EVENT_UNWATCH_GROUP, () -> "MY_GROUP").send();
    context.newEvent(EVENT_UNWATCH_GROUP, () -> "SSL_CERTIFICATES").send();
}
```

## Best Practices

### ✅ **Recommended Patterns**

```java
// Use descriptive group keys
final String GROUP_KEY = "HTTP_CLIENT_CERTIFICATES";

// Watch directories, not individual files (more efficient)
.putR("paths", List.of("/etc/ssl/certs"))  // ✅ Directory
// Instead of listing every .pem file individually

// Handle all change types appropriately
nano.subscribeEvent(EVENT_FILE_CHANGE, event -> {
    final String kind = event.asString("kind");
    switch (kind) {
        case "ENTRY_CREATE" -> handleFileCreated(event.payload());
        case "ENTRY_MODIFY" -> handleFileModified(event.payload());
        case "ENTRY_DELETE" -> handleFileDeleted(event.payload());
    }
});

// Use broadcast for config changes (following best practices)
context.newEvent(EVENT_CONFIG_CHANGE, () -> configChanges)
    .broadcast(true) // Ensure all listeners receive
    .send();
```

### ❌ **Anti-Patterns**

```java
// Don't watch individual files if you can avoid it
.putR("paths", List.of("/path/file1.txt", "/path/file2.txt")) // ❌

// Don't create groups for single-use scenarios
final String GROUP_KEY = "TEMP_GROUP_" + UUID.randomUUID(); // ❌

// Don't forget to unregister groups on shutdown
// Missing cleanup can cause resource leaks // ❌
```

## Concurrency & Performance

### Thread Safety
- **Read Operations**: Multiple threads can safely read group information simultaneously
- **Write Operations**: Group registration/unregistration is atomic and thread-safe
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
        // Watch Kubernetes-mounted config maps
        final TypeMap k8sGroup = new TypeMap()
            .putR("groupKey", "K8S_CONFIGMAPS")
            .putR("paths", List.of(
                "/etc/config",           // ConfigMap mount
                "/etc/secrets",          // Secret mount
                "/opt/app/config"        // Additional config volume
            ));

        context.newEvent(EVENT_WATCH_GROUP, () -> k8sGroup).send();
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

**High memory usage:**
```java
// Monitor watched groups and paths
final Set<String> groups = fileWatcher.getWatchedGroups();
groups.forEach(group -> {
    final Set<Path> paths = fileWatcher.getGroupPaths(group);
    System.out.println("Group " + group + " watches " + paths.size() + " paths");
});
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