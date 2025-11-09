> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**HttpServer**](README.md)

* [Overview](#overview)
* [Quick Start](#quick-start)
* [Configuration](#configuration)
* [Events](#events)

# HTTP Service

The `HttpServer` is Nano's built-in HTTP service that handles web requests and responses. It's designed to be simple, fast, and flexible - perfect for building REST APIs, web applications, and microservices.

## Overview

Nano's HTTP service provides:
- **Non-blocking request handling** using virtual threads
- **Built-in CORS support** with automatic origin handling
- **Flexible request/response processing** through events
- **SSL/TLS support** with multiple certificate formats and hot reloads
- **Type-safe request parsing** with automatic JSON conversion
- **Comprehensive error handling** at multiple levels

## Quick Start

Here's a complete example of a user registration API:

```java
public class UserApi {
    public static void main(String[] args) {
        final Nano nano = new Nano(args, new HttpServer());
        
        // CORS handling (must be first)
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserApi::handleCors);
        
        // Authentication middleware
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserApi::handleAuth);
        
        // API endpoints
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserApi::handleRegister);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, UserApi::handleLogin);
        
        // Global error handling
        nano.subscribeError(EVENT_HTTP_REQUEST, UserApi::handleError);
    }
    
    // CORS handling
    private static void handleCors(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodOptions)
            .ifPresent(req -> req.createCorsResponse().respond(event));
    }
    
    // Authentication middleware
    private static void handleAuth(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(req -> req.pathMatch("/api/**"))
            .filter(req -> !req.pathMatch("/api/auth/**")) // Skip auth for login/register
            .filter(req -> !isValidToken(req.authToken()))
            .ifPresent(req -> req.createResponse()
                .statusCode(401)
                .body(Map.of("error", "Unauthorized"))
                .respond(event));
    }
    
    // User registration
    private static void handleRegister(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/auth/register"))
            .ifPresent(req -> {
                final TypeMap body = req.bodyAsJson().asMap();
                final String email = body.asString("email");
                final String password = body.asString("password");
                
                // Validate input
                if (email == null || password == null) {
                    req.createResponse()
                        .statusCode(400)
                        .body(Map.of("error", "Email and password required"))
                        .respond(event);
                    return;
                }
                
                // Process registration
                try {
                    final User user = createUser(email, password);
                    req.createResponse()
                        .statusCode(201)
                        .body(Map.of("id", user.getId(), "email", user.getEmail()))
                        .respond(event);
                } catch (ValidationException e) {
                    req.createResponse()
                        .statusCode(400)
                        .body(Map.of("error", e.getMessage()))
                        .respond(event);
                }
            });
    }
    
    // User login
    private static void handleLogin(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/auth/login"))
            .ifPresent(req -> {
                final TypeMap body = req.bodyAsJson().asMap();
                final String email = body.asString("email");
                final String password = body.asString("password");
                
                try {
                    final String token = authenticateUser(email, password);
                    req.createResponse()
                        .statusCode(200)
                        .body(Map.of("token", token, "user", getUserByEmail(email)))
                        .respond(event);
                } catch (AuthenticationException e) {
                    req.createResponse()
                        .statusCode(401)
                        .body(Map.of("error", "Invalid credentials"))
                        .respond(event);
                }
            });
    }
    
    // Global error handler
    private static void handleError(Event<?, ?> event) {
        if (event.isEvent(EVENT_HTTP_REQUEST)) {
            event.payloadAck()
                .createResponse()
                .statusCode(500)
                .body(Map.of("error", "Internal server error", 
                           "details", event.error().getMessage()))
                .respond(event);
        }
    }
}
```

## Usage

### Start Http Service

A) As startup [Service](../../services/README.md): `new Nano(new HttpServer())`

B) Contextual `context.run(new HttpServer())` - this way its possible to provide a custom configuration.

### Handle HTTP Requests

The Event listener are executed in order of subscription.
This makes it possible to define authorization rules before the actual request is processed.

```java
public static void main(final String[] args) {
    final Nano app = new Nano(args, new HttpServer());

    // Authorization
    app.subscribeEvent(EVENT_HTTP_REQUEST, RestEndpoint::authorize);

    // Response
    app.subscribeEvent(EVENT_HTTP_REQUEST, RestEndpoint::helloWorldController);

    // Error handling for requests that bubbled up
    app.subscribeEvent(EVENT_HTTP_REQUEST_UNHANDLED, RestEndpoint::controllerAdvice);
}

private static void helloWorldController(final Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(HttpObject::isMethodGet)
        .filter(request -> request.pathMatch("/hello"))
        .ifPresent(request -> request.respond(event, response -> response.body(Map.of("Hello", System.getProperty("user.name")))));
}

private static void authorize(final Event<HttpObject, HttpObject> event) {
    event.payloadOpt()
        .filter(request -> request.pathMatch("/hello/**"))
        .filter(request -> !"mySecretToken".equals(request.authToken()))
        .ifPresent(request -> request.respond(event, response -> response.body(Map.of("message", "You are unauthorized")).statusCode(401)));
}

private static void controllerAdvice(final Event<HttpObject, HttpObject> event) {
    event.payloadOpt().ifPresent(request ->
        request.respond(event, response -> response.body("Internal Server Error [" + event.error().getMessage() + "]").statusCode(500)));
}
```

## Configuration

| [Config](../../context/README.md#configuration) | Type      | Default                       | Description                                                 |
|-------------------------------------------------|-----------|-------------------------------|-------------------------------------------------------------|
| `app_service_http_port`                         | `Integer` | `8080`, `8081`, ... (dynamic) | The HTTP/HTTPS port to bind                                 |
| `app_service_http_client`                       | `Boolean` | `false`                       | If HttpClient should start with HttpServer                  |
| `app_service_https_cert`                        | `String`  | `null`                        | Path to the server certificate (PEM/CRT)                    |
| `app_service_https_key`                         | `String`  | `null`                        | Path to the private key (PEM)                               |
| `app_service_https_ca`                          | `String`  | `null`                        | Optional CA cert path                                       |
| `app_service_https_kts`                         | `String`  | `null`                        | Path to keystore (JKS, JCEKS, PKCS12)                       |
| `app_service_https_password`                    | `String`  | `null`                        | Optional password for private key or keystore               |
| `app_service_https_certs`                       | `String`  | `null`                        | Comma-separated list of cert/key/store files or directories |

### TLS Hot Reloading

When HTTPS configs are present, the `HttpServer` automatically registers the relevant certificate/key paths with the [FileWatcher](../filewatcher/README.md). Any `ENTRY_MODIFY` events emitted for that watcher group (`CONFIG_SERVICE_HTTPS_CERT`) trigger an internal `EVENT_FILE_CHANGE` listener that rebuilds the SSL context without restarting the server. This allows you to rotate certificates by simply overwriting the files.

You can also push new locations through `EVENT_CONFIG_CHANGE`. Broadcasting a map with updated `app_service_https_*` entries causes the server to reconfigure itself and refresh the watcher list. Successful updates keep handling requests seamlessly; invalid paths leave the previous SSL context in place but subsequent HTTPS requests will fail, matching the behaviour verified in the regression tests.

To benefit from automatic reloads make sure the FileWatcher service is running; otherwise only explicit `EVENT_CONFIG_CHANGE` events will refresh the TLS state.

## Events

| In 🔲 <br/> Out 🔳 | [Event](../../events/README.md) | Payload           | Response     | Description                                                                                                                                                                          |
|--------------------|---------------------------------|-------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 🔲                 | `EVENT_HTTP_REQUEST`            | `HttpObject`      | `HttpObject` | Triggered when an HTTP request is received.<br/>If a response is returned for this event, it is sent back to the client.                                                             |
| 🔲                 | `EVENT_HTTP_REQUEST_UNHANDLED`  | `HttpObject`      | `HttpObject` | Triggered when an HTTP request reached the end of the pipeline without a response.<br/>Respond here to customize the default 404                                                     |
| 🔲                 | `EVENT_APP_ERROR`               | `HttpObject`      | `HttpObject` | Triggered when an exception occurs while handling an HTTP request.<br/>If a response is returned for this event, it is sent back to the client.<br/>Else client will receive a `500` |
| 🔲                 | `EVENT_HTTP_REQUEST`            | `HttpObject`      | `HttpObject` | Listening for HTTP request                                                                                                                                                           |
| 🔲                 | `EVENT_FILE_CHANGE`             | `FileChangeEvent` | `Void`       | When the FileWatcher is active and the HTTPS watcher group fires, the server refreshes its SSL context automatically.                                                                |
| 🔲                 | `EVENT_CONFIG_CHANGE`           | `TypeMap`         | `Void`       | Pushing new `app_service_https_*` keys causes the server to reload TLS assets and re-register the watcher group.                                                                     |
