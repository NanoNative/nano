package org.nanonative.nano.examples;

import org.nanonative.nano.core.Nano;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.HttpService;
import org.nanonative.nano.services.http.model.HttpObject;
import org.junit.jupiter.api.Disabled;

import java.util.Map;

import static org.nanonative.nano.core.model.Context.EVENT_APP_UNHANDLED;
import static org.nanonative.nano.services.http.HttpService.EVENT_HTTP_REQUEST;

@Disabled
public class HttpReceive {

    public static void main(final String[] args) {
        final Nano app = new Nano(args, new HttpService());

        // Authorization
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::authorize);

        // Response
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::helloWorldController);

        // Error handling
        app.subscribeEvent(EVENT_APP_UNHANDLED, HttpReceive::controllerAdvice);

        // CORS
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::cors);
    }








    private static void cors(final Event event) {
        event.payloadOpt(HttpObject.class)
            .filter(HttpObject::isMethodOptions)
            .ifPresent(request -> request.corsResponse().respond(event));
    }

    private static void authorize(final Event event) {
        event.payloadOpt(HttpObject.class)
            .filter(request -> request.pathMatch("/hello/**"))
            .filter(request -> !"mySecretToken".equals(request.authToken()))
            .ifPresent(request -> request.response().body(Map.of("message", "You are unauthorized")).statusCode(401).respond(event));
    }

    private static void helloWorldController(final Event event) {
        event.payloadOpt(HttpObject.class)
            .filter(HttpObject::isMethodGet)
            .filter(request -> request.pathMatch("/hello"))
            .ifPresent(request -> request.response().body(Map.of("Hello", System.getProperty("user.name"))).respond(event));
    }

    private static void controllerAdvice(final Event event) {
        event.payloadOpt(HttpObject.class).ifPresent(request ->
            request.response().body("Internal Server Error [" + event.error().getMessage() + "]").statusCode(500).respond(event));
    }
}
