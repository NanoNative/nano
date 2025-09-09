> [Home](../../../README.md) / **[ErrorHandling](README.md)**

# Error Handling

Error handling is pretty straight forward in Nano.
All errors are [Events](../../events/README.md) which are logged automatically with the [LogService](../../services/logger/README.md)
from the caller [Context](../../context/README.md).
These [Events](../../events/README.md) are send to the `EVENT_EVENT_APP_ERROR` channel and can be caught or intercepted.

## Error Channel

The channel `EVENT_EVENT_APP_ERROR` is used for all errors and also unhandled http events.
_See [HttpServer](../../services/httpserver/README.md) for more information._
Therefore, its necessary to filter the right events to catch.

## Handle Error

To handle an error, you can subscribe to the `EVENT_EVENT_APP_ERROR` channel and filter the events by the `error`
property.
`event.acknowledge()` is optional to stop further processing. _(Cough or Intercept)_

```java
 public static void main(final String[] args) {
    final Context context = new Nano(args).context(ErrorHandling.class);

    // Listen to exceptions
    context.subscribeError(EVENT_APP_ERROR, event -> {
        // Print error message
        event.context().warn(() -> "Caught event [{}] with error [{}] ", event.channel().name(), event.error().getMessage());
        event.acknowledge(); // Set exception as handled (prevent further processing)
    });

    // Listen to exceptions to specific event channel
    context.subscribeError(EVENT_HTTP_REQUEST, event -> {
        // Print error message
        event.context().warn(() -> "Caught event [{}] with error [{}] ", event.channel().name(), event.error().getMessage());
        event.acknowledge(); // Set exception as handled (prevent further processing)
    });

    // Throw an exception
    context.run(() -> {
        throw new RuntimeException("Test Exception");
    });
}
```
