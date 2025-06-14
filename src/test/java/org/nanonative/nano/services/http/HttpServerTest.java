package org.nanonative.nano.services.http;

import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.model.HttpObject;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.services.http.HttpClient.CONFIG_HTTP_CLIENT_TRUST_ALL;
import static org.nanonative.nano.services.http.HttpServer.*;

class HttpServerTest {

    // openssl req -x509 -sha512 -nodes -days 99999 -newkey rsa:2048 -keyout server_simple.key -out server_simple.crt -subj "/CN=localhost"
    static final Path SIMPLE_CERT = getResourcePath("HttpServer/server_simple.crt");
    static final Path SIMPLE_KEY = getResourcePath("HttpServer/server_simple.key");
    // openssl req -x509 -sha512 -nodes -days 99999 -newkey rsa:2048 -keyout server_simple.key -out server_simple.crt -subj "/CN=localhost"
    static final Path PASSWORD_CERT = getResourcePath("HttpServer/server_pw.crt");
    static final Path PASSWORD_KEY = getResourcePath("HttpServer/server_pw.key");
    // openssl pkcs12 -export -out server_keystore.p12 -inkey server_simple.key -in server_simple.crt -passout pass:testpassword
    static final Path PKCS12_STORE = getResourcePath("HttpServer/server_keystore.p12");
    // cat server_simple.crt server_simple.crt > chained.crt
    static final Path CHAINED_CERT = getResourcePath("HttpServer/chained.crt");

    @Test
    void testSimpleCertAndKey() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(
            CONFIG_SERVICE_HTTPS_CERT, SIMPLE_CERT,
            CONFIG_SERVICE_HTTPS_KEY, SIMPLE_KEY,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.response().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsString()).isEqualTo("ok");

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testPkcs12Keystore() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(
            CONFIG_SERVICE_HTTPS_KTS, PKCS12_STORE,
            CONFIG_SERVICE_HTTPS_PASSWORD, "testpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.response().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.bodyAsString()).isEqualTo("ok");
        assertThat(response.statusCode()).isEqualTo(200);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testPasswordProtectedCertAndKey() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(
            CONFIG_SERVICE_HTTPS_CERT, PASSWORD_CERT,
            CONFIG_SERVICE_HTTPS_KEY, PASSWORD_KEY,
            CONFIG_SERVICE_HTTPS_PASSWORD, "testpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.response().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.bodyAsString()).isEqualTo("ok");
        assertThat(response.statusCode()).isEqualTo(200);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testChainedCertWithSimpleKey() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(
            CONFIG_SERVICE_HTTPS_CERT, CHAINED_CERT,
            CONFIG_SERVICE_HTTPS_KEY, SIMPLE_KEY,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.response().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsString()).isEqualTo("ok");

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    // TODO: if response is null, means no client running - should this be as error message returned?
    @Test
    void testEmptyContextDefaultsToHttp() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(CONFIG_SERVICE_HTTP_CLIENT, true), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt(HttpObject.class)
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.response().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("http://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsString()).isEqualTo("ok");

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    private static Path getResourcePath(String resource) {
        URL res = HttpServerTest.class.getClassLoader().getResource(resource);
        assertThat(res).as("Resource not found: " + resource).isNotNull();
        return Paths.get(res.getPath());
    }
}
