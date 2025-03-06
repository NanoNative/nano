> [Home](../../../README.md) / **[ErrorHandling](README.md)**

# Error Handling

Error handling is pretty straight forward in Nano.
All errors are [Events](../../events/README.md) which are logged automatically with the [LogService](../../services/logger/README.md)
from the caller [Context](../../context/README.md).
These [Events](../../events/README.md) are send to the `EVENT_APP_UNHANDLED` channel and can be caught or intercepted.

## Error Channel

The channel `EVENT_APP_UNHANDLED` is used for all errors and also unhandled http events.
_See [HttpServer](../../services/httpserver/README.md) for more information._
Therefore, its necessary to filter the right events to catch. Error events usually have a non nullable `error` property.

## Handle Error

To handle an error, you can subscribe to the `EVENT_APP_UNHANDLED` channel and filter the events by the `error`
property.
`event.acknowledge()` is optional to stop further processing. _(Cough vs Intercept)_

```java
public static void main(final String[] args) {
    final Context context = new Nano(args).context(ErrorHandling.class);

    // Listen to exceptions
    context.subscribeEvent(EVENT_APP_UNHANDLED, event -> {
        // Print error message
        event.context().warn(() -> "Caught event [{}] with error [{}] ", event.nameOrg(), event.error().getMessage());
        event.acknowledge(); // Set exception as handled (prevent further processing)
    });

    // Throw an exception
    context.run(() -> {
        throw new RuntimeException("Test Exception");
    });
}
```
