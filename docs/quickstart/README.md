> [Home](../../README.md) / **[Quick Start](README.md)**

# Quick Start Guide

Welcome to Nano! This guide will help you understand Nano's philosophy and get you building real applications quickly.

## 🎯 What is Nano?

Nano is a **lightweight, event-driven microservice framework** that helps you write clean, functional Java applications. Think of it as a minimal alternative to Spring Boot that focuses on:

- **Simplicity**: No annotations, no magic, just plain Java
- **Performance**: Built for GraalVM native compilation and virtual threads
- **Flexibility**: Event-driven architecture that scales naturally

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

### Services vs Controllers vs Static Methods

In Nano, you don't need complex object hierarchies. Here's how to think about organizing your code:

**❌ Traditional Spring-style approach:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
    
    public User createUser(UserDto dto) {
        // business logic mixed with infrastructure
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
                event.context().sendEvent(EVENT_DATABASE_QUERY, 
                    Map.of("query", "INSERT INTO users...", "params", body));
            });
    }
}
```

### Key Principles

1. **Static Methods for Business Logic**: Keep your domain logic in static methods
2. **Services for Infrastructure**: Use services for external systems (database, HTTP, etc.)
3. **Events for Communication**: Services communicate through events, not direct calls
4. **Context for Everything**: Access configuration, logging, and utilities through the context

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

1. **Think Functionally**: Use static methods for business logic, not classes with state
2. **Events Connect Everything**: Services communicate through events, not direct method calls
3. **Context is Your Friend**: Access configuration, logging, and utilities through the context
4. **Keep It Simple**: Nano is designed to reduce complexity, not add it
5. **Test Everything**: Since everything is functional, testing is straightforward

## 🚀 Next Steps

- [Core Concepts](../info/concept/README.md) - Deep dive into Nano's philosophy
- [HTTP Service Guide](../services/httpserver/README.md) - Complete HTTP service documentation
- [Event System](../events/README.md) - Master Nano's event-driven architecture
- [Examples](../../src/test/java/org/nanonative/nano/examples/) - More code examples

Ready to build something amazing? Let's go! 🚀
