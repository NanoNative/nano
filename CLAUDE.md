# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nano is a lightweight Java microservice framework that revolutionizes development by eliminating traditional OOP patterns (Controllers, Services, Repositories) in favor of static event listeners that react to events in a functional, stateless manner.

**Core Philosophy:**
- **Static Methods, Not Objects**: Business logic lives in static methods, not service objects
- **Event-Driven Communication**: Everything communicates through events, not direct method calls
- **Universal Services**: Services are generic connectors for external systems - no business logic
- **TypeMap Integration**: Built-in type conversion using Berlin Yuna's TypeMap library
- **Global Error Handling**: Errors are events that can be subscribed to globally

## Build & Development Commands

### Maven Commands
- **Build**: `./mvnw clean compile` or `mvn clean compile`
- **Test**: `./mvnw test` or `mvn test`
- **Package**: `./mvnw clean package` or `mvn clean package`
- **Coverage Report**: `./mvnw clean verify` (generates Jacoco coverage in `target/site/jacoco/`)
- **Release Build**: `./mvnw clean deploy -P release` (requires GPG setup)

### GraalVM Native Image
- Add the native-image profile to pom.xml and run: `mvn package -Pnative-image`
- Project is designed to be fully compilable with GraalVM for native executables

## Architecture & Code Structure

### Core Components
- **`Nano`**: Main application class extending `NanoServices`
- **`Context`**: Central hub for configuration, event channels, and service management
- **`Event<T, R>`**: Generic event system with payload and response types
- **`Service`**: Base interface for external system connectors (HTTP, databases, etc.)
- **`Channel`**: Event routing and subscription mechanism

### Key Directories
- **`src/main/java/org/nanonative/nano/core/`**: Core framework classes
- **`src/main/java/org/nanonative/nano/services/`**: Built-in services (HTTP, logging, metrics, file watching)
- **`src/main/java/org/nanonative/nano/helper/`**: Utility classes and event system
- **`src/test/java/org/nanonative/nano/examples/`**: Working examples and patterns
- **`docs/`**: Comprehensive documentation

### Event-Driven Pattern
```java
// Static event listener - no objects needed!
public static void handleRequest(Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(HttpObject::isMethodPost)
        .filter(req -> req.pathMatch("/users"))
        .ifPresent(req -> {
            // Business logic here
            event.context().sendEvent(EVENT_CREATE_USER, req.bodyAsJson().asMap());
            req.createResponse().statusCode(200).respond(event);
        });
}
```

### Built-in Services
- **HttpServer/HttpClient**: Non-blocking HTTP handling with SSL support
- **LogService**: Structured logging with JSON and console formatters
- **MetricService**: Application metrics collection
- **FileWatcher**: File system monitoring

## Development Guidelines

### Code Conventions
- **Java 21**: Uses virtual threads from Project Loom
- **No Reflection**: Framework avoids reflection for GraalVM compatibility
- **Static Methods**: Business logic in static methods, not instance methods
- **Event Naming**: Use constants like `EVENT_HTTP_REQUEST`, `EVENT_USER_CREATE`
- **Conventional Commits**: Every commit message follows the Conventional Commits spec (e.g., `feat: add metric cache eviction`)
- **TypeMap Usage**: Leverage TypeMap for data conversion and manipulation
- **Clean Code**: Follow clean code principles as mentioned in CONTRIBUTING.md

### Testing
- **JUnit 5**: Primary testing framework with AssertJ for assertions
- **Example-Driven**: Check `src/test/java/org/nanonative/nano/examples/` for patterns
- **Test Coverage**: Jacoco configured with coverage reporting

### Key Examples to Reference
- **`SimpleUserApi.java`**: Complete REST API example
- **`HttpReceive.java`**: HTTP server patterns
- **`HttpSend.java`**: HTTP client patterns
- **`ErrorHandling.java`**: Global error handling patterns
- **`MetricCreation.java`**: Application metrics patterns

## Dependencies
- **Berlin Yuna TypeMap**: Core dependency for type conversion and data handling
- **JUnit 5 + AssertJ**: Testing stack
- **No Spring/Micronaut**: Nano is designed as an alternative to traditional frameworks

## Configuration
- Environment variables, system properties, and command-line arguments supported
- Context-based configuration management
- Production mode: `CONFIG_ENV_PROD=true`
