> [Home](../../README.md) / **[Quick Start](README.md)**

# Quick Start Guide

Welcome to Nano! This guide will help you understand Nano's philosophy and get you building real applications quickly.

## 🎯 What is Nano?

Nano is a **lightweight approach to microservice development** that breaks away from traditional OOP patterns. Instead of creating complex object hierarchies with Controllers, Services, and Repositories, Nano uses **static event listeners** that react to events in a functional, stateless manner.

**Key Philosophy:**
- **Static Methods, Not Objects**: Business logic lives in static methods, not service objects
- **Event-Driven Communication**: Everything communicates through events, not direct method calls
- **Universal Services**: Services are generic connectors for external systems (databases, HTTP, queues) - no business logic
- **TypeMap Everywhere**: Built-in type conversion and data transformation using TypeMap
- **Global Error Handling**: Even errors are events that can be subscribed to and handled globally

## 🚀 Your First Nano Application

Let's build a simple user registration API to understand Nano's approach:

```java
public class UserRegistrationApp {
    public static void main(String[] args) {
        // Start Nano with HTTP server
        final Nano nano = new Nano(args, new HttpServer());
        
        // Register user endpoint
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserController::handleRegister);
        
        // Login endpoint  
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserController::handleLogin);
        
        // Global error handling
        nano.subscribeError(EVENT_HTTP_REQUEST, UserController::handleError);
    }
}
```

## 🏗️ Nano's Architecture Philosophy

### The Nano Way: Static Methods + Events + Universal Services

Nano fundamentally changes how you think about application architecture. Instead of complex object hierarchies, you use:

1. **Static Methods for Business Logic** - No service objects needed
2. **Events for Communication** - Everything flows through events
3. **Universal Services for Infrastructure** - Services handle external systems only

**❌ Traditional Spring-style approach:**
```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;
    
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.createUser(dto));
    }
}

@Service
public class UserService {
    @Autowired
    private UserRepository repository;
    
    public User createUser(UserDto dto) {
        // Business logic mixed with infrastructure concerns
        return repository.save(new User(dto));
    }
}
```

**✅ Nano's functional approach:**
```java
public class UserController {
    // Static methods for business logic
    public static void handleRegister(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/auth/register"))
            .ifPresent(req -> {
                // Extract data
                final TypeMap body = req.bodyAsJson().asMap();
                
                // Validate and process
                final String email = body.asString("email");
                final String password = body.asString("password");
                
                // Send to database service
                event.context().sendEvent(EVENT_DATABASE_QUERY, Map.of("query", "INSERT INTO users...", "params", body));
            });
    }
}

// Database Service - Universal Connector for external systems
public class DatabaseService extends Service {
    @Override
    public void onEvent(Event<?, ?> event) {
        if (event.isEvent(EVENT_CREATE_USER)) {
            // Pure infrastructure - no business logic here!
            final TypeMap userData = event.payloadAsMap();
            final User user = createUser(userData);
            event.respond(user);
        }
    }
}
```

### Key Principles

1. **Static Methods for Business Logic**: Keep your domain logic in static methods - no service objects needed!
2. **Universal Services for Infrastructure**: Services handle external systems only (database, HTTP, queues) - no business logic
3. **Event-Driven Communication**: Everything communicates through events - HTTP requests, database operations, errors
4. **TypeMap for Data Handling**: Automatic type conversion for JSON, XML, and any data format - no DTOs needed
5. **Context for Everything**: Access configuration, logging, and utilities through the context
6. **Global Error Handling**: Subscribe to error events for centralized error management

## 📡 Event-Driven Communication

Nano's power comes from its event system. Here's how different parts of your application communicate:

```java
public class DatabaseService extends Service {
    @Override
    public void onEvent(Event<?, ?> event) {
        if (event.isEvent(EVENT_DATABASE_QUERY)) {
            final TypeMap query = event.payloadAsMap();
            final String sql = query.asString("query");
            final TypeMap params = query.asMap("params");
            
            // Execute query
            final List<TypeMap> results = executeQuery(sql, params);
            
            // Send response back
            event.reply(results);
        }
    }
}
```

## 🔧 Configuration Made Simple

Nano uses a simple configuration system that works with any format:

**application.properties:**
```properties
app_service_http_port=8080
app_log_level=INFO
app_config_database_url=jdbc:postgresql://localhost:5432/mydb
```

**Access in code:**
```java
public static void handleRequest(Event<HttpObject, HttpObject> event) {
    final Context context = event.context();
    
    // Get configuration values
    final int port = context.asInt("app_service_http_port");
    final String dbUrl = context.asString("app_config_database_url");
    
    // Use them in your logic
    context.info(() -> "Server running on port {}", port);
}
```

## 🌐 HTTP Service Best Practices

### Request Handling

```java
public static void handleApiRequest(Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(HttpObject::isMethodPost)
        .filter(req -> req.pathMatch("/api/users"))
        .ifPresent(req -> {
            // Parse JSON body
            final TypeMap body = req.bodyAsJson().asMap();
            
            // Get headers
            final String authToken = req.headerMap().asString("Authorization");
            
            // Process request
            processUserCreation(body, authToken, event);
        });
}
```

### Response Handling

```java
private static void processUserCreation(TypeMap body, String authToken, Event<HttpObject, HttpObject> event) {
    try {
        // Business logic here
        final User user = createUser(body);
        
        // Send success response
        event.payloadAck()
            .createResponse()
            .statusCode(201)
            .body(Map.of("id", user.getId(), "email", user.getEmail()))
            .respond(event);
            
    } catch (ValidationException e) {
        // Send error response
        event.payloadAck()
            .createResponse()
            .statusCode(400)
            .body(Map.of("error", e.getMessage()))
            .respond(event);
    }
}
```

### CORS Handling

```java
public static void handleCors(Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(HttpObject::isMethodOptions)
        .ifPresent(req -> req.createCorsResponse().respond(event));
}
```

## 🛡️ Error Handling

Nano provides multiple levels of error handling:

### Global Error Handler
```java
nano.subscribeError(EVENT_HTTP_REQUEST, event -> {
    final String errorMessage = event.error().getMessage();
    
    event.payload()
        .createResponse()
        .failure(500, "Internal server error", errorMessage)
        .respond(event);
});
```

### Service-Level Error Handling
```java
public class DatabaseService extends Service {
    @Override
    public void onFailure(Event<?, ?> event, Throwable error) {
        // Log the error
        event.context().error(() -> "Database error: {}", error.getMessage());
        
        // Send error response if it's an HTTP request
        if (event.isEvent(EVENT_HTTP_REQUEST)) {
            event.payloadAck()
                .createResponse()
                .statusCode(500)
                .body(Map.of("error", "Database unavailable"))
                .respond(event);
        }
    }
}
```

## 🏢 Organizing Larger Applications

For bigger applications, organize your code like this:

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

**Main Application:**
```java
public class MyApp {
    public static void main(String[] args) {
        final Nano nano = new Nano(args, 
            new HttpServer(), 
            new DatabaseService(), 
            new EmailService()
        );
        
        // Register all controllers
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserController::handleRegister);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserController::handleLogin);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, AuthController::handleTokenValidation);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, ApiController::handleApiRequest);
        
        // Global error handling
        nano.subscribeError(EVENT_HTTP_REQUEST, MyApp::handleGlobalError);
    }
    
    private static void handleGlobalError(Event<?, ?> event) {
        // Global error handling logic
    }
}
```

## 🎯 Key Takeaways

1. **Think Events, Not Objects**: Use static methods for business logic, not service objects with state
2. **Universal Services**: Services are pure infrastructure connectors - no business logic mixed in
3. **TypeMap Everywhere**: Automatic type conversion eliminates the need for DTOs and mappers
4. **Event-Driven Everything**: HTTP requests, database operations, errors - all flow through events
5. **Context is Your Friend**: Access configuration, logging, and utilities through the context
6. **Global Error Handling**: Subscribe to error events for centralized error management
7. **Keep It Simple**: Nano is designed to reduce complexity, not add it
8. **Test Everything**: Since everything is functional, testing is straightforward

## 🚀 Next Steps

- [Core Concepts](../info/concept/README.md) - Deep dive into Nano's philosophy
- [HTTP Service Guide](../services/httpserver/README.md) - Complete HTTP service documentation
- [Event System](../events/README.md) - Master Nano's event-driven architecture
- [Examples](../../src/test/java/org/nanonative/nano/examples/) - More code examples

Ready to build something amazing? Let's go! 🚀
