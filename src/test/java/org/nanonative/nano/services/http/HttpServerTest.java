package org.nanonative.nano.services.http;

import berlin.yuna.typemap.model.TypeMap;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.model.HttpObject;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.services.http.HttpClient.CONFIG_HTTP_CLIENT_TRUSTED_CA;
import static org.nanonative.nano.services.http.HttpClient.CONFIG_HTTP_CLIENT_TRUST_ALL;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_CERT;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KEY;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_KTS;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTPS_PASSWORD;
import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTP_CLIENT;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

class HttpServerTest {

    // openssl req -x509 -newkey rsa:2048 -sha512 -nodes -days 9999 -subj "/CN=localhost" -keyout server_simple.key -out server_simple.crt
    static final Path SIMPLE_CERT = getResourcePath("HttpServer/server_simple.crt");
    static final Path SIMPLE_KEY = getResourcePath("HttpServer/server_simple.key");

    // openssl genrsa -aes256 -passout pass:testpassword -out server_pw.key 2048
    // openssl req -new -x509 -sha512 -days 9999 -key server_pw.key -passin pass:testpassword -out server_pw.crt -subj "/CN=localhost"
    static final Path PASSWORD_CERT = getResourcePath("HttpServer/server_pw.crt");
    static final Path PASSWORD_KEY = getResourcePath("HttpServer/server_pw.key");

    // openssl pkcs12 -export -inkey server_simple.key -in server_simple.crt -out server_keystore.p12 -passout pass:testpassword
    static final Path PKCS12_STORE = getResourcePath("HttpServer/server_keystore.p12");

    // cat server_simple.crt server_simple.crt > chained.crt
    static final Path CHAINED_CERT = getResourcePath("HttpServer/chained.crt");

    // cp server_simple.crt server.pem && cp server_simple.key server.key
    static final Path PEM_CERT = getResourcePath("HttpServer/server.pem");
    static final Path PEM_KEY = getResourcePath("HttpServer/server.key");

    // keytool -genkeypair -alias testkey -keyalg RSA -keysize 2048 -keystore server.jks -storepass testpassword -keypass testpassword -validity 9999 -dname "CN=localhost"
    static final Path JKS_STORE = getResourcePath("HttpServer/server.jks");

    // keytool -genkeypair -alias testkey -keyalg RSA -keysize 2048 -keystore server.jceks -storetype JCEKS -storepass testpassword -keypass testpassword -validity 9999 -dname "CN=localhost"
    static final Path JCEKS_STORE = getResourcePath("HttpServer/server.jceks");

    @Test
    void testSimpleCertAndKey() {
        testHttpsServer(SIMPLE_CERT, SIMPLE_KEY, null);
    }

    @Test
    void testPkcs12Keystore() {
        testHttpsServerWithKeystore(PKCS12_STORE);
    }

    @Test
    void testJksKeystore() {
        testHttpsServerWithKeystore(JKS_STORE);
    }

    @Test
    void testJceksKeystore() {
        testHttpsServerWithKeystore(JCEKS_STORE);
    }

    @Test
    void testPasswordProtectedCertAndKey() {
        testHttpsServer(PASSWORD_CERT, PASSWORD_KEY, "testpassword");
    }

    @Test
    void testChainedCertWithSimpleKey() {
        testHttpsServer(CHAINED_CERT, SIMPLE_KEY, null);
    }

    @Test
    void testPemCertAndKey() {
        testHttpsServer(PEM_CERT, PEM_KEY, null);
    }

    @Test
    void testEmptyContextDefaultsToHttp() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(Map.of(CONFIG_SERVICE_HTTP_CLIENT, true), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.createResponse().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("http://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsString()).isEqualTo("ok");

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testInvalidCertPath() {
        final Path invalidCert = Paths.get("nonexistent.crt");
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, invalidCert,
            CONFIG_SERVICE_HTTPS_KEY, SIMPLE_KEY,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.statusCode()).isNotEqualTo(200);
        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testInvalidKeyPath() {
        final Path invalidKey = Paths.get("nonexistent.key");
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, SIMPLE_CERT,
            CONFIG_SERVICE_HTTPS_KEY, invalidKey,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.statusCode()).isNotEqualTo(200);
        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testWrongPasswordForKey() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, PASSWORD_CERT,
            CONFIG_SERVICE_HTTPS_KEY, PASSWORD_KEY,
            CONFIG_SERVICE_HTTPS_PASSWORD, "wrongpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.statusCode()).isNotEqualTo(200);
        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testInvalidKeystorePath() {
        final Path invalidStore = Paths.get("nonexistent.p12");
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_KTS, invalidStore,
            CONFIG_SERVICE_HTTPS_PASSWORD, "testpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.statusCode()).isNotEqualTo(200);
        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testInvalidKeystorePassword() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_KTS, PKCS12_STORE,
            CONFIG_SERVICE_HTTPS_PASSWORD, "wrongpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.statusCode()).isNotEqualTo(200);
        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testSimpleCertWithTrustedClient() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, SIMPLE_CERT,
            CONFIG_SERVICE_HTTPS_KEY, SIMPLE_KEY,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUSTED_CA, SIMPLE_CERT
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
            .filter(req -> req.pathMatch("/secure"))
            .ifPresent(req -> req.createResponse().body("secured").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/secure")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.bodyAsString()).isEqualTo("secured");
        assertThat(response.statusCode()).isEqualTo(200);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    @Test
    void testInvalidTrustedClient() {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, SIMPLE_CERT,
            CONFIG_SERVICE_HTTPS_KEY, SIMPLE_KEY,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUSTED_CA, "AA" + SIMPLE_CERT
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
            .filter(req -> req.pathMatch("/secure"))
            .ifPresent(req -> req.createResponse().body("secured").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/secure")
            .send(nano.context(HttpServerTest.class));

        assertThat(response.bodyAsString()).isEqualTo("Failed to send HTTP request - maybe no [HttpClient] was configured?");
        assertThat(response.statusCode()).isEqualTo(-99);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    private static void testHttpsServer(Path cert, Path key, String password) {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_CERT, cert,
            CONFIG_SERVICE_HTTPS_KEY, key,
            CONFIG_SERVICE_HTTPS_PASSWORD, password,
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.createResponse().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.bodyAsString()).isEqualTo("ok");
        assertThat(response.statusCode()).isEqualTo(200);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    private static void testHttpsServerWithKeystore(Path kts) {
        final HttpServer server = new HttpServer();
        final Nano nano = new Nano(TypeMap.mapOf(
            CONFIG_SERVICE_HTTPS_KTS, kts,
            CONFIG_SERVICE_HTTPS_PASSWORD, "testpassword",
            CONFIG_SERVICE_HTTP_CLIENT, true,
            CONFIG_HTTP_CLIENT_TRUST_ALL, true
        ), server);

        nano.subscribeEvent(EVENT_HTTP_REQUEST, event -> event.payloadOpt()
            .filter(req -> req.pathMatch("/test"))
            .ifPresent(req -> req.createResponse().body("ok").respond(event)));

        final HttpObject response = new HttpObject().path("https://localhost:" + server.address().getPort() + "/test").send(nano.context(HttpServerTest.class));
        assertThat(response.bodyAsString()).isEqualTo("ok");
        assertThat(response.statusCode()).isEqualTo(200);

        nano.stop(nano.context(HttpServerTest.class)).waitForStop();
    }

    private static Path getResourcePath(String resource) {
        URL res = HttpServerTest.class.getClassLoader().getResource(resource);
        assertThat(res).as("Resource not found: " + resource).isNotNull();
        return Paths.get(res.getPath());
    }

    // TODO: error handling test with 404 and 500 exceptions and interceptors
}
