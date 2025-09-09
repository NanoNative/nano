> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**HttpClient**](README.md)

* [Configuration](#configuration)
* [Events](#events)

# Http Client

Is a default [Services](../../services/README.md) of Nano which is responsible for sending HTTP requests with built-in
support for:

* Automatic retry
* Timeout handling
* Redirects
* TLS/SSL Trust customization

## Usage

### Start Http Service

A) As startup [Service](../../services/README.md): `new Nano(new HttpClient())`

B) Contextual `context.run(new HttpClient())` - this way its possible to provide a custom configuration.

### Intercept HTTP Requests

The Event listener are executed in order of subscription.
This makes it possible to define change data before sending the HTTP request.

```java
public static void main(final String[] args) {
    final Nano app = new Nano(args, new HttpClient());

    // Intercept and add Token to request
    app.subscribeEvent(EVENT_HTTP_REQUEST, event ->
        event.payloadOpt().ifPresent(request -> request.header("Authorization", "myCustomToken"))
    );

    app.context(MyClass.class).newEvent(EVENT_SEND_HTTP, () -> new Httpobject().methodType(GET).path("http://localhost:8080/hello").body("Hello World")).send();
}
```

### Send HTTP Requests

```java
import org.nanonative.nano.services.http.HttpClient;

public static void main(final String[] args) {
    final Context context = new Nano(args, new HttpClient()).context(MyClass.class);

    // send request via event
    final HttpObject response1 = context.event(EVENT_SEND_HTTP, () -> new HttpObject()
        .methodType(GET)
        .path("http://localhost:8080/hello")
        .body("Hello World")
    ).send().response();

    // send request via context
    final HttpObject response2 = new HttpObject()
        .methodType(GET)
        .path("http://localhost:8080/hello")
        .body("Hello World")
        .send(context);

    // send request manually
    final HttpObject response3 = new HttpClient().send(new HttpObject()
        .methodType(GET)
        .path("http://localhost:8080/hello")
        .body("Hello World")
    );
}
```

## Configuration

| [Config](../../context/README.md#configuration) | Type      | Default | Description                                                                                                                                                               |
|-------------------------------------------------|-----------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app_service_http_version`                      | `Integer` | `2`     | Use HTTP/1 or HTTP/2 protocol                                                                                                                                             |
| `app_service_http_max_retries`                  | `Integer` | `3`     | Max automatic retries on failure                                                                                                                                          |
| `app_service_http_con_timeout_ms`               | `Integer` | `5000`  | Connection timeout in milliseconds                                                                                                                                        |
| `app_service_http_read_timeout_ms`              | `Integer` | `10000` | Read timeout in milliseconds                                                                                                                                              |
| `app_service_http_follow_redirects`             | `Boolean` | `true`  | Automatically follow redirects (3xx)                                                                                                                                      |
| `app_service_http_trust_all`                    | `Boolean` | `false` | Trust all SSL certificates (unsafe, but useful for dev environments)                                                                                                      |
| `app_service_http_trusted_ca`                   | `String`  | `null`  | Path to trusted CA certificate file or folder. If "default", uses OS & Java-level CA trust bundles (/etc/ssl/certs, /etc/pki/..., and ${JAVA_HOME}/lib/security/cacerts). |

## Events

| In 🔲 <br/> Out 🔳 | [Event](../../events/README.md) | Payload      | Response     | Description           |
|--------------------|---------------------------------|--------------|--------------|-----------------------|
| 🔳                 | `EVENT_SEND_HTTP`               | `HttpObject` | `HttpObject` | Sending a HttpRequest |

