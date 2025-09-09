> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**HttpServer**](README.md)

* [Usage](#usage)
    * [Start HTTP Service](#start-http-service)
    * [Handle HTTP Requests](#handle-http-requests)
* [Configuration](#configuration)
* [Events](#events)

# Http Service

The `HttpServer` is a built-in [Service](../../services/README.md) of Nano responsible for handling HTTP and HTTPS
requests.  
Each request is processed concurrently using a thread pool.

Full support for **SSL/TLS** is included.  
Use PEM, CRT, PKCS#12, JKS, or JCEKS formats—individually or mixed. Certificates can be **hot-loaded from files or
directories**.

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

    // Error handling
    app.subscribeEvent(EVENT_APP_UNHANDLED, RestEndpoint::controllerAdvice);
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

## Events

| In 🔲 <br/> Out 🔳 | [Event](../../events/README.md) | Payload      | Response     | Description                                                                                                                                                                          |
|--------------------|---------------------------------|--------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 🔲                 | `EVENT_HTTP_REQUEST`            | `HttpObject` | `HttpObject` | Triggered when an HTTP request is received.<br/>If a response is returned for this event, it is sent back to the client.                                                             |
| 🔲                 | `EVENT_HTTP_REQUEST_UNHANDLED`  | `HttpObject` | `HttpObject` | Triggered when an HTTP request is received but not handled.<br/>If a response is returned for this event, it is sent back to the client.<br/>Else client will receive a `404         |
| 🔲                 | `EVENT_APP_UNHANDLED`           | `HttpObject` | `HttpObject` | Triggered when an exception occurs while handling an HTTP request.<br/>If a response is returned for this event, it is sent back to the client.<br/>Else client will receive a `500` |
| 🔲                 | `EVENT_HTTP_REQUEST`            | `HttpObject` | `HttpObject` | Listening for HTTP request                                                                                                                                                           |

