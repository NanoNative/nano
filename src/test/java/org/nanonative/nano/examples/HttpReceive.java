package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

@Disabled
public class HttpReceive {

    public static void main(final String[] args) {
        final Nano app = new Nano(args, new HttpServer());

        // Authorization
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::authorize);

        // Response
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::helloWorldController);

        // Error handling
        app.subscribeError(EVENT_HTTP_REQUEST, HttpReceive::controllerAdvice);

        // CORS
        app.subscribeEvent(EVENT_HTTP_REQUEST, HttpReceive::cors);
    }

    private static void cors(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodOptions)
            .ifPresent(request -> request.createCorsResponse().respond(event));
    }

    private static void authorize(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(request -> request.pathMatch("/hello/**"))
            .filter(request -> !"mySecretToken".equals(request.authToken()))
            .ifPresent(request -> request.createResponse().body(Map.of("message", "You are unauthorized")).statusCode(401).respond(event));
    }

    private static void helloWorldController(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodGet)
            .filter(request -> request.pathMatch("/hello"))
            .ifPresent(request -> request.createResponse().body(Map.of("Hello", System.getProperty("user.name"))).respond(event));
    }

    private static void controllerAdvice(final Event<HttpObject, HttpObject> event) {
        event.payloadAck().createResponse().body("Internal Server Error [" + event.error().getMessage() + "]").statusCode(500).respond(event);
    }
}
