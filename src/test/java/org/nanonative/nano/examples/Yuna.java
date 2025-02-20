package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.services.http.HttpService;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.services.http.HttpService.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;

@Disabled
public class Yuna {

    public static void main(final String[] args) {

        final Nano nano = new Nano(new HttpService());

        final Date myConfigValue = nano.context().asDate("unix-timestamp");
        nano.logger().info(() -> "Config value converted to date [{}]", myConfigValue);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(HttpObject::isMethodGet)
            .filter(req -> req.pathMatch("/hello"))
            .ifPresent(req -> req.corsResponse()
                .statusCode(200)
                .body(Map.of("Nano", "World"))
                .respond(event))
        );




        final Context context = nano.context(Yuna.class);
        nano.logger().info(() -> "Hello World 1");
        context.sendEvent(EVENT_CONFIG_CHANGE, Map.of(CONFIG_LOG_LEVEL, Level.OFF));
        nano.logger().info(() -> "Hello World 2");
        nano.stop(context);
//
//        // Nano with configuration
//        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, LogLevel.INFO));
//
//        // Nano with startup services
//        final Nano nano = new Nano(new HttpService());
//
//        // Nano adding "Hello World" API
//        final Nano nano = new Nano(new HttpService())
//            .subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
//                .filter(HttpObject::isMethodGet)
//                .filter(request -> request.pathMatch("/hello"))
//                .ifPresent(request -> request.response().body(System.getProperty("user.name")).send(event))
//            );


        //TODO: Dynamic Queues to Services
        //TODO: Dynamic Messages to Services
        //TODO: support internationalization (logRecord.setResourceBundle(javaLogger.getResourceBundle());, logRecord.setResourceBundleName(javaLogger.getResourceBundleName()))
//        final Nano application = new Nano(Map.of(
//            CONFIG_LOG_LEVEL, LogLevel.INFO,
//            CONFIG_LOG_FORMATTER, "console"
//        ), new LogQueue(), new MetricService(), new HttpService());


    }
}
