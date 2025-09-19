package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;

import static org.nanonative.nano.core.model.Context.EVENT_APP_ERROR;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

@Disabled
public class ErrorHandling {

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
}
