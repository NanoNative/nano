package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import static org.nanonative.nano.services.http.HttpClient.EVENT_SEND_HTTP;
import static org.nanonative.nano.services.http.model.HttpMethod.GET;

@Disabled
@SuppressWarnings("java:S1854") // Suppress "dead code" warning
public class HttpSend {

    public static void main(final String[] args) {
        final Context context = new Nano(args, new HttpServer()).context(HttpSend.class);

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
}
