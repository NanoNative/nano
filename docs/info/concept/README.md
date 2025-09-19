> [Home](../../../README.md) / **[Concept](README.md)**

# Core Concepts

Nano is a minimalistic approach to microservice development that fundamentally changes how you think about application architecture. Instead of creating complex object hierarchies with Controllers, Services, and Repositories, Nano uses **static event listeners** that react to events in a functional, stateless manner.

Nano is a **tool, not a framework**, designed to bring you back to the basics of clean, functional Java programming. Emphasizes simplicity, security, and efficiency

## 🎯 What Makes Nano Different?

**🚫 Traditional OOP Problems Nano Solves:**
- **No More Object Hierarchies**: Forget Controllers, Services, Repositories - just static methods and events!
- **No Dependency Injection**: No complex IoC containers or `@Autowired` annotations
- **No Reflection**: No runtime magic or performance overhead
- **No Complex Configuration**: No XML files or complex setup

**✅ Nano's minimalistic Approach:**
- **Static Methods for Business Logic**: Your domain logic lives in static methods, not service objects
- **Event-Driven Communication**: Everything communicates through events, not direct method calls
- **Universal Services**: Services are generic connectors for external systems (databases, HTTP, queues) - no business logic
- **TypeMap Everywhere**: Built-in type conversion and data transformation using TypeMap
- **Global Error Handling**: Even errors are events that can be subscribed to and handled globally

## 🔄 Traditional vs Nano Approach

### Traditional Spring Boot Approach
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody UserDto userDto) {
        try {
            User user = userService.createUser(userDto);
            return ResponseEntity.ok(user);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public User createUser(UserDto dto) {
        // Business logic here
        return userRepository.save(new User(dto));
    }
}
```

### Nano Approach
```java
public class UserController {
    
    // Static method handles HTTP request - no @Controller needed!
    public static void handleRegister(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/users/register"))
            .ifPresent(req -> event.context().sendEvent(EVENT_CREATE_USER, req.bodyAsJson().asMap()));
    }
}

// Database Service - Universal Connector for external systems
public class DatabaseService extends Service {
    
    @Override
    public void onEvent(Event<?, ?> event) {
        if (event.isEvent(EVENT_CREATE_USER)) {
            final TypeMap userData = event.payloadAsMap();
            final User user = createUser(userData);
            event.respond(user);
        }
    }
}
```

**🎯 Key Differences:**
- **No Annotations**: Everything is explicit and visible - no magic!
- **No Dependency Injection**: Services communicate through events, not direct calls
- **No Complex Configuration**: Simple property-based setup through Context
- **Static Methods**: Business logic in static methods, not service objects
- **Universal Services**: Services are pure infrastructure connectors - no business logic
- **Event-Driven**: Everything flows through events - HTTP requests, database operations, errors

### Modern and Fluent Design 🚀

Nano leverages fluent chaining and functional programming styles to create a syntax that resembles a stateless scripting
language. By avoiding annotations and other “black magic,” Nano maintains transparency and simplicity in its codebase.
Fluent and chaining means, there are no `get` and `set` prefixes and no `void` returns for methods.

### No External Dependencies 🔒

Nano is built without any foreign dependencies, ensuring a lean, secure library free from common vulnerabilities and
excessive dependencies. This results in a smaller, faster, and more secure codebase. You only need to trust and know the
license agreements of Nano.

### Minimal Resource Consumption 🌱

Nano is engineered for a minimal environmental footprint, utilizing fewer resources and making garbage collection more
efficient due to its functional programming style.

### Non-Blocking Virtual Threads 🧵

Nano utilizes non-blocking virtual threads from [Project Loom](https://jdk.java.net/loom/) to enhance efficiency and
performance. These threads maximize CPU utilization without blocking the main thread, eliminating the need for manual
thread limit settings.
Note that Nano cannot control Java’s built-in `ForkJoinPool` used for `java.util.concurrent` objects like streams.
To optimize performance, it is recommended to set the Java property to something like
this `-Djava.util.concurrent.ForkJoinPool.common.parallelism=100.` in case of high parallelism.

### GraalVM Compatibility ⚡

Nano is fully compatible with [GraalVM](https://www.graalvm.org), allowing you to compile native executables that do not
require a JVM to run. This feature is particularly useful in containerized and serverless environments.
Nano avoids reflection and dynamic class loading, ensuring seamless [GraalVM](https://www.graalvm.org) integration
without additional configuration.

### Extensible and Open 🪶

All Nano functions and classes are `public` or `protected`, allowing developers to extend or modify the library as
needed. This breaks the concept of immutable objects, but we think it's more important to be able to extend and modify
Nano than closing it. Means, every developer is responsible for the own code!
We still encourages contributions and improvements from the community.

### Modular Design 🧩

Nano’s [Event](../../events/README.md) system enables decoupling of functions, plugin
creation ([Services](../../services/README.md)), and function interception.
For example, you can globally control and respond to every error that occurs, similar to a global `Controller Advice`.
With that its also easy to change configurations on the fly.
This modular design allows services, such as the built-in [HttpServer](../../services/httpserver/README.md) and
[MetricService](../../services/metricservice/README.md), to operate independently while still being able to interact
when started.

### Service-Based Architecture 📊

**Services in Nano are Universal Connectors, Not Business Objects**

([Services](../../services/README.md)) in Nano function as **universal connectors** for external systems - databases, HTTP services, queues, and other technologies. They contain **no business logic** and are designed to be pure infrastructure adapters.

**Key Principles:**
- **No Business Logic**: Services handle external integrations only - no domain logic
- **Event-Driven**: Services communicate through events, not direct method calls
- **Universal Design**: One service can handle multiple types of external systems
- **Easy Testing**: Services can be easily replaced with fake implementations in tests
- **Decoupled Architecture**: Business logic stays in static methods, infrastructure in services

**Example Service Design:**
```java
public class DatabaseService extends Service {
    
    @Override
    public void onEvent(Event<?, ?> event) {
        // Handle different database operations through events
        if (event.isEvent(EVENT_DATABASE_QUERY)) {
            executeQuery(event.payloadAsMap());
        } else if (event.isEvent(EVENT_DATABASE_INSERT)) {
            insertRecord(event.payloadAsMap());
        }
        // No business logic here - just database operations!
    }
}
```

This approach simplifies testing, as services and components can be tested independently without the need for mocking or stubbing. You execute only what you define, avoiding the pitfalls of auto-applying dependencies.

### TypeMap - The Heart of Data Handling 🔄

**TypeMap eliminates the need for custom DTOs and complex object mapping!**

Nano's built-in `TypeMap` system provides automatic type conversion and data transformation for `JSON`, `XML`, and any data format. This eliminates the need for custom DTOs, mappers, and complex object hierarchies.

**How TypeMap Works:**
- **Automatic Conversion**: HTTP requests, database results, and any data automatically convert to TypeMap
- **Lazy Evaluation**: Fields are converted to the requested type only when accessed
- **Type Safety**: Built-in type checking and conversion with fallback values
- **Universal Format**: Same TypeMap format works across HTTP requests, database operations, and events

**Example - No More DTOs Needed:**
```java
// ❌ Traditional approach with DTOs
public class UserDto {
    private String name;
    private String email;
    // getters, setters, constructors...
}

// ✅ Nano approach with TypeMap
public static void handleUser(Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(HttpObject::isMethodPost)
        .ifPresent(req -> {
            final TypeMap userData = req.bodyAsJson().asMap();
            
            // Direct access with type conversion - no DTOs needed!
            final String name = userData.asString("name");
            final String email = userData.asString("email");
            final int age = userData.asInt("age", 0); // with default value
            
            // Process user data...
        });
}
```

**TypeMap Benefits:**
- **No DTOs**: Eliminate the need for custom data transfer objects
- **No Mappers**: No manual mapping between different object types
- **Type Safety**: Built-in type checking and conversion
- **Performance**: Lazy evaluation and efficient memory usage
- **Simplicity**: One format for all data handling

_See [TypeMap](https://github.com/YunaBraska/type-map) for more information._

### Configuration Management ⚙️

Nano uses a [Context](../../context/README.md) object to manage logging, tracing and configurations.
Nano reads property files and profiled properties which all end up in the [Context](../../context/README.md) Object.
The properties can be converted to the required types as needed.
This eliminates the need for custom configuration objects.




