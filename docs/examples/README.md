> [Home](../../README.md) / **[Examples](README.md)**

# Nano Examples & Common Solutions

Welcome to Nano's practical examples guide! This page contains real-world solutions and common patterns for building microservices with Nano's event-driven architecture.

## 🎯 What is Nano?

**Nano is a lightweight approach to microservice development** that breaks away from traditional OOP patterns. Instead of creating complex object hierarchies with Controllers, Services, and Repositories, Nano uses **static event listeners** that react to events in a functional, stateless manner.

### Philosophy & Usage

**Key Philosophy:**
- **Static Methods, Not Objects**: Business logic lives in static methods, not service objects
- **Event-Driven Communication**: Everything communicates through events, not direct method calls
- **Universal Services**: Services are generic connectors for external systems (databases, HTTP, queues) - no business logic
- **TypeMap Everywhere**: Built-in type conversion and data transformation using TypeMap
- **Global Error Handling**: Even errors are events that can be subscribed to and handled globally

**How Nano is Meant to be Used:**
- Start with `new Nano(args)` - simple like other frameworks
- Use static methods for business logic - no service objects needed
- Subscribe to events for HTTP requests, database operations, errors
- Services handle external integrations only - no business logic mixed in
- Everything flows through events - HTTP requests, database operations, errors

## 📋 Table of Contents

- [CORS Handling](#-cors-handling)
- [Path Parameters](#-path-parameters)
- [Wildcard Parameters](#-wildcard-parameters)
- [Authentication](#-authentication)
- [Sending Events](#-sending-events)
- [Testing](#-testing)
- [Error Handling](#-error-handling)
- [Profiles](#-profiles)
- [Configuration](#-configuration)
- [Defining Config Keys](#-defining-config-keys)
- [Defining Events](#-defining-events)
- [Starting Nano](#-starting-nano)
- [Change Configs on the Fly](#-change-configs-on-the-fly)
- [Schedulers](#-schedulers)
- [Logging](#-logging)
- [Metrics](#-metrics)

---

## 🌐 CORS Handling

*Related: [HTTP Server Guide](../services/httpserver/README.md) | [Event System](../events/README.md)*

### Basic CORS for OPTIONS Requests

Handle preflight CORS requests automatically:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodOptions)
    .ifPresent(req -> req.createCorsResponse().respond(event))
);
```

### CORS with Response Data

Include CORS headers in your actual responses:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodGet)
    .filter(req -> req.pathMatch("/api/hello"))
    .ifPresent(req -> req.createCorsResponse()
        .status(200)
        .body("Hello World")
        .respond(event))
);
```

---

## 🛣️ Path Parameters

*Related: [HTTP Server Guide](../services/httpserver/README.md) | [Event System](../events/README.md)*

### Single Path Parameter

Extract path parameters from URLs:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodGet)
    .filter(req -> req.pathMatch("/user/{userId}/children"))
    .ifPresent(req -> {
        String userId = req.pathParams().asString("userId");
        // Process user with ID: userId
        req.createResponse()
            .body("User ID: " + userId)
            .respond(event);
    })
);
```

---

## 🎯 Wildcard Parameters

*Related: [HTTP Server Guide](../services/httpserver/README.md) | [Event System](../events/README.md)*

### Single Path Segment Wildcard

Match any single path segment:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodGet)
    .filter(req -> req.pathMatch("/user/*/children"))
    .ifPresent(req -> {
        // Matches /user/123/children, /user/john/children, etc.
        req.createResponse().body("User children endpoint").respond(event);
    })
);
```

### Multiple Path Segments Wildcard

Match any number of path segments:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodGet)
    .filter(req -> req.pathMatch("/user/**"))
    .ifPresent(req -> {
        // Matches /user/123, /user/123/profile, /user/123/settings/account, etc.
        req.createResponse().body("User endpoint").respond(event);
    })
);
```

---

## 🔐 Authentication

*Related: [HTTP Server Guide](../services/httpserver/README.md) | [Event System](../events/README.md) | [Error Handling](../info/errorhandling/README.md)*

### Token-Based Authentication

Implement authentication with token validation:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(req -> req.pathMatch("/api/**"))
    .filter(req -> !req.pathMatch("/api/public/**"))
    .ifPresent(req -> {
        if (!isValidToken(req.authToken())) {
            req.createCorsResponse()
                .failure(401, "Authentication required", null)
                .respond(event);
        } else {
            // Add scopes to event context for downstream processing
            event.put("scopes", List.of("extracted:from:token:write", "read", "something"));
        }
    })
);

private boolean isValidToken(String token) {
    // Your token validation logic here
    return token != null && !token.isEmpty();
}
```

---

## 📡 Sending Events

*Related: [HTTP Client Guide](../services/httpclient/README.md) | [Event System](../events/README.md) | [Context Management](../context/README.md)*

### HTTP Client Setup

Initialize Nano with HTTP client to send HTTP events:

```java
final Nano nano = new Nano(args, new HttpClient());
```

### Sending HTTP Requests

Send HTTP requests to external services:

```java
final HttpObject response = context.newEvent(EVENT_SEND_HTTP, () -> new HttpObject()
    .methodType(GET)
    .path("http://localhost:8080/hello")
    .body("Hello World")
).send().response();

// Process the response
if (response.statusCode() == 200) {
    String responseBody = response.bodyAsString();
    // Handle successful response
}
```

---

## 🧪 Testing

*Related: [Services Guide](../services/README.md) | [Context Management](../context/README.md) | [Event System](../events/README.md)*

### Integration Testing Setup

Create comprehensive integration tests:

```java
public class MyServiceTest {
    
    @Test
    public void testUserRegistration() {
        // Initialize Nano with required services
        final Nano nano = new Nano(new HttpServer(), new HttpClient(), new FakeService());
        
        // Subscribe to events for testing
        nano.subscribeEvent(EVENT_HTTP_REQUEST, MyController::handleRegister);
        
        // Send test events
        final HttpObject request = new HttpObject()
            .methodType(POST)
            .path("/api/users/register")
            .body(Map.of("email", "test@example.com", "password", "password123"));
            
        final HttpObject response = nano.context(MyServiceTest.class)
            .newEvent(EVENT_HTTP_REQUEST, () -> request)
            .send()
            .response();
            
        // Assert response
        assertEquals(201, response.statusCode());
        
        // Gracefully shutdown
        nano.stop(nano.context(MyServiceTest.class)).waitForStop();
    }
}
```

### Test Lifecycle Management

Use `@Before` and `@After` for test setup:

```java
public class MyServiceTest {
    private Nano nano;
    
    @Before
    public void setUp() {
        nano = new Nano(new HttpServer(), new HttpClient(), new FakeService());
        // Setup test environment
    }
    
    @After
    public void tearDown() {
        nano.stop(nano.context(MyServiceTest.class)).waitForStop();
    }
}
```

---

## 🛡️ Error Handling

*Related: [Error Handling Guide](../info/errorhandling/README.md) | [Event System](../events/README.md) | [Context Management](../context/README.md)*

### Event-Specific Error Handling

Handle errors for specific event types:

```java
nano.subscribeError(EVENT_HTTP_REQUEST, event -> {
    // Log the error with custom level
    event.put("level", LogLevel.DEBUG);
    
    // Get the original request
    final HttpObject request = event.channel(EVENT_HTTP_REQUEST);
    
    // Send error response
    request.createResponse()
        .statusCode(500)
        .body("Internal Server Error: " + event.error().getMessage())
        .respond(event);
});
```

### Global Error Handling

Handle all application errors:

```java
nano.subscribeError(EVENT_APP_ERROR, event -> {
    // Get the original event that caused the error
    final Event<?, ?> originalEvent = event.payload();
    
    // Log the error
    event.context().error(() -> "Global error: {}", event.error().getMessage());
    
    // Handle different event types
    originalEvent.channel(EVENT_HTTP_REQUEST).ifPresent(originalEvent -> {
        final HttpObject request = originalEvent.payload();
        request.createResponse()
            .statusCode(500)
            .body("Service temporarily unavailable")
            .respond(originalEvent);
    });
});
```

### Error Response Patterns

```java
// Acknowledge error to prevent default logging
event.acknowledge();

// Or respond which triggers acknowledgement
event.respond(myCustomResponse);
```

---

## 📁 Profiles

*Related: [Context Management](../context/README.md) | [Configuration](#-configuration)*

### Profile Configuration

Use profile-specific configuration files:

**application.properties** (base configuration):
```properties
app_service_http_port=8080
app_log_level=INFO
```

**application-production.properties**:
```properties
app_service_http_port=80
app_log_level=WARN
app_database_url=jdbc:postgresql://prod-db:5432/mydb
```

**application-test.properties**:
```properties
app_service_http_port=8081
app_log_level=DEBUG
app_database_url=jdbc:h2:mem:testdb
```

### Profile Activation

Activate profiles through various methods:

```bash
# Environment variable
export app_profiles=production

# JVM parameter
-Dapp_profiles=production

# Command line argument
java -jar myapp.jar app_profiles=production

# Programmatic
new Nano(Map.of("app_profiles", "test"))
```

### Profile Synonyms

Nano also supports common framework profile keys:

```properties
# All of these work
app_profiles=production
spring.profiles.active=production
quarkus.profile=production
micronaut.profiles=production
```

---

## ⚙️ Configuration

*Related: [Context Management](../context/README.md) | [Profiles](#-profiles) | [Registers](../registers/README.md)*

### Accessing Configuration

All configuration sources end up in the context:

```java
public static void handleRequest(Event<HttpObject, HttpObject> event) {
    final Context context = event.context();
    
    // Access configuration with type conversion
    final int port = context.asInt("app_service_http_port");
    final String dbUrl = context.asString("app_database_url");
    final boolean debugMode = context.asBool("app_debug_mode", false);
    
    // Use configuration in your logic
    context.info(() -> "Server running on port {}", port);
}
```

### Configuration Sources

Nano automatically loads configuration from:
- `application.properties` files
- Environment variables
- Command line arguments
- System properties
- DSL configuration

---

## 🔑 Defining Config Keys

*Related: [Registers](../registers/README.md) | [Context Management](../context/README.md)*

### Register Configuration Keys

Define reusable configuration keys:

```java
public class AppConfig {
    // Register config keys for help menu and reusability
    public static final ConfigKey APP_HELP = ConfigRegister.registerConfig("help", "Lists available config keys");
    public static final ConfigKey APP_PORT = ConfigRegister.registerConfig("port", "HTTP server port");
    public static final ConfigKey APP_LOG_LEVEL = ConfigRegister.registerConfig("log_level", "Logging level");
    public static final ConfigKey APP_DATABASE_URL = ConfigRegister.registerConfig("database_url", "Database connection URL");
}

// Usage
final boolean showHelp = context.asBool(APP_HELP);
final int port = context.asInt(APP_PORT, 8080);
```

---

## 📢 Defining Events

*Related: [Event System](../events/README.md) | [Registers](../registers/README.md)*

### Event Channel Registration

Define custom event channels:

```java
public class AppEvents {
    // Define event channels with input/output types
    public static final ChannelId EVENT_SEND_HTTP = registerChannelId("SEND_HTTP", HttpObject.class, HttpObject.class);
    public static final ChannelId EVENT_USER_CREATED = registerChannelId("USER_CREATED", User.class, User.class);
    public static final ChannelId EVENT_EMAIL_SEND = registerChannelId("EMAIL_SEND", EmailRequest.class, EmailResponse.class);
}

// Usage
context.sendEvent(EVENT_USER_CREATED, newUser);
```

---

## 🚀 Starting Nano

*Related: [Quick Start Guide](../quickstart/README.md) | [Context Management](../context/README.md) | [Event System](../events/README.md)*

### Simple Startup

Start Nano like other frameworks:

```java
public static void main(String[] args) {
    // Simple startup
    final Nano nano = new Nano(args);
    
    // Register event handlers
    nano.subscribeEvent(EVENT_HTTP_REQUEST, MyController::handleRequest);
}
```

### Recommended Pattern with Context

Use context for better logging and developer experience:

```java
public static void main(String[] args) {
    final Nano nano = new Nano(args);
    
    // Create context for your main class
    final Context context = nano.context(MyApplication.class);
    
    // Register handlers with context
    nano.subscribeEvent(EVENT_HTTP_REQUEST, MyController::handleRequest);
    
    context.info(() -> "Application started successfully");
}
```

---

## 🔄 Change Configs on the Fly

*Related: [Event System](../events/README.md) | [Context Management](../context/README.md) | [Services Guide](../services/README.md)*

### Dynamic Configuration Updates

Update configuration at runtime:

```java
// Send configuration change event
context.sendEvent(EVENT_CONFIG_CHANGE, Map.of(
    "app_log_level", "DEBUG",
    "app_service_http_port", "9090"
));

// Services can react to configuration changes
public class MyService extends Service {
    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        // React to configuration changes
        if (changes.containsKey("app_log_level")) {
            String newLevel = changes.asString("app_log_level");
            updateLogLevel(newLevel);
        }
    }
}
```

### HTTP-Triggered Configuration Changes

Connect HTTP requests to configuration updates:

```java
nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
    .filter(HttpObject::isMethodPost)
    .filter(req -> req.pathMatch("/admin/config"))
    .ifPresent(req -> {
        final TypeMap configChanges = req.bodyAsJson().asMap();
        
        // Send configuration change event
        event.context().sendEvent(EVENT_CONFIG_CHANGE, configChanges);
        
        req.createResponse()
            .statusCode(200)
            .body("Configuration updated")
            .respond(event);
    })
);
```

---

## ⏰ Schedulers

*Related: [Schedulers Guide](../schedulers/README.md) | [Context Management](../context/README.md)*

### Delayed Execution

Run tasks after a delay:

```java
context.run(() -> {
    context.info(() -> "Task executed after delay");
}, 5, TimeUnit.SECONDS);
```

### Daily Tasks

Run tasks daily at specific times:

```java
// Run daily at 8:00 AM
context.runDaily(() -> {
    context.info(() -> "Daily backup started");
}, LocalTime.of(8, 0, 0));
```

### Periodic Tasks

Run tasks at regular intervals:

```java
// Run every 30 seconds, starting after 10 seconds
context.run(() -> {
    context.info(() -> "Periodic task executed");
}, 10, 30, TimeUnit.SECONDS);
```

### Weekly Tasks

Run tasks weekly on specific days:

```java
// Run every Monday at 9:00 AM
context.runWeekly(() -> {
    context.info(() -> "Weekly maintenance started");
}, LocalTime.of(9, 0, 0), DayOfWeek.MONDAY);
```

---

## 📝 Logging

*Related: [Logger Service](../services/logger/README.md) | [Context Management](../context/README.md)*

### Basic Logging

Use Nano's built-in logging:

```java
public static void handleRequest(Event<HttpObject, HttpObject> event) {
    final Context context = event.context();
    
    // Simple logging with placeholders
    context.info(() -> "Hello {}", "World");
    context.debug(() -> "Processing request: {}", requestId);
    context.error(() -> "Error occurred: {}", errorMessage);
}
```

### Log Configuration

Configure logging behavior:

```properties
# Log level configuration
app_log_level=DEBUG

# Log format (json or console)
app_log_formatter=console
```

### Log Formatting

#### Console Format
```java
context.info(() -> "Hello {}", "World");
// Output: [2024-11-11 11:11:11.111] [INFO] [MyClass] - Hello World
```

#### JSON Format
```java
context.debug(() -> "Hello {}", "World");
// Output: {"Hello":"World", "level":"DEBUG","logger":"MyClass","message":"Hello World","timestamp":"2024-11-11 11:11:11.111"}
```

### Placeholder Patterns

```java
// Basic placeholders
context.info(() -> "User {} logged in", username);

// Indexed placeholders
context.info(() -> "User {0} logged in at {1}", username, timestamp);

// Mixed placeholders
context.info(() -> "Processing {} items, {} completed", totalItems, completedItems);
```

---

## 📊 Metrics

*Related: [Metric Service Guide](../services/metricservice/README.md) | [Event System](../events/README.md) | [Context Management](../context/README.md)*

### Metric Service Setup

Initialize Nano with metrics service:

```java
final Nano nano = new Nano(args, new MetricService());
```

### Available Metric Endpoints

The metric service provides endpoints for different monitoring systems:

- `/metrics/influx` - InfluxDB format
- `/metrics/dynamo` - DynamoDB format  
- `/metrics/wavefront` - Wavefront format
- `/metrics/prometheus` - Prometheus format

### Creating Metrics

Send metric updates:

```java
// Create metric tags
final Map<String, String> metricTags = Map.of(
    "service", "user-service",
    "endpoint", "/api/users",
    "method", "POST"
);

// Send counter metric
context.newEvent(EVENT_METRIC_UPDATE)
    .payload(() -> new MetricUpdate(COUNTER, "my.counter.key", 130624, metricTags))
    .send();

// Send gauge metric
context.newEvent(EVENT_METRIC_UPDATE)
    .payload(() -> new MetricUpdate(GAUGE, "active.connections", 42, metricTags))
    .send();
```

### Metric Types

```java
// Counter - increments over time
new MetricUpdate(COUNTER, "requests.total", 1, tags)

// Gauge - current value
new MetricUpdate(GAUGE, "memory.usage", 85.5, tags)

// Timer - duration measurements
new MetricUpdate(TIMER_START, "my.timer.key", null, metricTags)
new MetricUpdate(TIMER_END, "my.timer.key", null, metricTags)
```

---

## 🎯 Best Practices

*Related: [Core Concepts](../info/concept/README.md) | [Services Guide](../services/README.md) | [Event System](../events/README.md)*

### Code Organization

```
src/main/java/
├── MyApp.java                 # Main application entry point
├── controllers/
│   ├── UserController.java    # Static methods for user operations
│   ├── AuthController.java    # Static methods for authentication
│   └── ApiController.java     # Static methods for API endpoints
├── services/
│   ├── DatabaseService.java   # Database operations
│   ├── EmailService.java      # Email sending
│   └── CacheService.java      # Caching operations
└── models/
    ├── User.java              # Data models
    └── ApiResponse.java       # Response models
```

### Event Handler Patterns

```java
public class UserController {
    
    // Handle user registration
    public static void handleRegister(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/users/register"))
            .ifPresent(req -> {
                final TypeMap userData = req.bodyAsJson().asMap();
                // Process registration
                event.context().sendEvent(EVENT_USER_CREATED, userData);
            });
    }
    
    // Handle user login
    public static void handleLogin(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/users/login"))
            .ifPresent(req -> {
                final TypeMap credentials = req.bodyAsJson().asMap();
                // Process login
                validateCredentials(credentials, event);
            });
    }
}
```

### Service Implementation

```java
public class DatabaseService extends Service {
    
    @Override
    public void onEvent(Event<?, ?> event) {
        event.channel(EVENT_USER_CREATED).ifPresent(e -> {
            final TypeMap userData = e.payloadAsMap();
            final User user = createUser(userData);
            e.respond(user);
        });
        event.channel(EVENT_USER_QUERY).ifPresent(e -> {
            final TypeMap query = event.payloadAsMap();
            final List<User> users = queryUsers(query);
            event.respond(users);
        });
    }
    
    private User createUser(TypeMap userData) {
        // Database operation - no business logic here!
        return new User(userData.asString("email"), userData.asString("name"));
    }
}
```

---

## 🚀 Next Steps

- [Quick Start Guide](../quickstart/README.md) - Complete walkthrough for beginners
- [Core Concepts](../info/concept/README.md) - Deep dive into Nano's philosophy
- [HTTP Service Guide](../services/httpserver/README.md) - Complete HTTP service documentation
- [Event System](../events/README.md) - Master Nano's event-driven architecture
- [Error Handling](../info/errorhandling/README.md) - Comprehensive error handling patterns

Ready to build something amazing with Nano? Let's go! 🚀
