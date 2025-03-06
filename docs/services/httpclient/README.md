> [Home](../../../README.md)
> / [Components](../../../README.md#-components)
> / [Services](../../services/README.md)
> / [**HttpClient**](README.md)

* [Configuration](#configuration)
* [Events](#events)

# Http Client

Is a default [Services](../../services/README.md) of Nano which is responsible for sending basic HTTP requests.

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
    
    app.context(MyClass.class).newEvent(EVENT_SEND_HTTP, () -> new Httpobject().methodType(GET).path("http://localhost:8080/hello").body("Hello World")).send();

    // Add Token to request
    app.subscribeEvent(EVENT_HTTP_REQUEST, event -> 
        event.payloadOpt(HttpObject.class).ifPresent(request -> request.header("Authorization", "myCustomToken"))
    );
}
```

### Send HTTP Requests

```java
import org.nanonative.nano.services.http.HttpClient;

public static void main(final String[] args) {
    final Context context = new Nano(args, new HttpClient()).context(MyClass.class);

    // send request via event
    final HttpObject response1 = context.sendEventR(EVENT_SEND_HTTP, () -> new HttpObject()
        .methodType(GET)
        .path("http://localhost:8080/hello")
        .body("Hello World")
    ).response(HttpObject.class);

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

| [Config](../../context/README.md#configuration) | Type      | Default                       | Description                                         |
|-------------------------------------------------|-----------|-------------------------------|-----------------------------------------------------|
| `app_service_http_version`                      | `Integer` | `2`                           | (HttpClient) Http Version 1 or 2                    |
| `app_service_http_max_retries`                  | `Integer` | `3`                           | (HttpClient) Maximum number of retries              |
| `app_service_http_con_timeoutMs`                | `Integer` | `5000`                        | (HttpClient) Connection timeout in milliseconds     |
| `app_service_http_read_timeoutMs`               | `Integer` | `10000`                       | (HttpClient) Read timeout in milliseconds           |
| `app_service_http_follow_redirects`             | `Boolean` | `true`                        | (HttpClient) Follow redirects                       |

## Events

| In 🔲 <br/> Out 🔳 | [Event](../../events/README.md) | Payload      | Response     | Description           |
|--------------------|---------------------------------|--------------|--------------|-----------------------|
| 🔳                 | `EVENT_SEND_HTTP`               | `HttpObject` | `HttpObject` | Sending a HttpRequest |

