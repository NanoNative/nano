package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_CERTS;

@Disabled
public class Yuna {

    public static void main(final String[] args) throws Exception {
        Path tempDirectory = Files.createTempDirectory("nano_test");
        Path crt = Files.createTempFile(tempDirectory, "nano", ".crt");
        Path key = Files.createTempFile(tempDirectory, "nano", ".key");

        final Nano nano = new Nano(Map.of(CONFIG_SERVICE_HTTPS_CERTS, tempDirectory), new HttpServer());
        // CRT, KEY
        // CRT, CA, KEY
        // PEM


//        System.exit(0);
//
//        final Date myConfigValue = nano.context().asDate("unix-timestamp");
//        nano.context().info(() -> "Config value converted to date [{}]", myConfigValue);
//
//        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
//            .filter(HttpObject::isMethodGet)
//            .filter(req -> req.pathMatch("/hello"))
//            .ifPresent(req -> req.corsResponse()
//                .statusCode(200)
//                .body(Map.of("Nano", "World"))
//                .respond(event))
//        );
//
//        final Context context = nano.context(Yuna.class);
//        context.info(() -> "Hello World 1");
//        context.sendEvent(EVENT_CONFIG_CHANGE, () -> Map.of(CONFIG_LOG_LEVEL, Level.OFF));
//        context.info(() -> "Hello World 2");
//        nano.stop(context);
//
//        // Nano with configuration
//        final Nano nano = new Nano(Map.of(CONFIG_LOG_LEVEL, LogLevel.INFO));
//
//        // Nano with startup services
//        final Nano nano = new Nano(new HttpServer());
//
//        // Nano adding "Hello World" API
//        final Nano nano = new Nano(new HttpServer())
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
//        ), new LogQueue(), new MetricService(), new HttpServer());


    }
}
