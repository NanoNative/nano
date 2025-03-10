package org.nanonative.nano.services.http;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import com.sun.net.httpserver.HttpExchange;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.core.model.Unhandled;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpHeaders;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static berlin.yuna.typemap.logic.TypeConverter.collectionOf;
import static org.nanonative.nano.core.model.Context.EVENT_APP_UNHANDLED;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.helper.event.EventChannelRegister.registerChannelId;
import static org.nanonative.nano.services.http.HttpsHelper.createHttpServer;

public class HttpServer extends Service {
    protected com.sun.net.httpserver.HttpServer server;

    // Register configurations
    public static final String CONFIG_SERVICE_HTTP_PORT = registerConfig("app_service_http_port", "Default port for the HTTP service (see " + HttpServer.class.getSimpleName() + ")");
    public static final String CONFIG_SERVICE_HTTP_CLIENT = registerConfig("app_service_http_client", "Boolean if " + HttpClient.class.getSimpleName() + " should start as well");
    public static final String CONFIG_SERVICE_HTTPS_CERTS = registerConfig("app_service_https_certs", "Comma-separated paths to SSL certificates, private keys, or keystores. Can be files or directories.");
    public static final String CONFIG_SERVICE_HTTPS_PASSWORD = registerConfig("app_service_https_password", "Optional password for SSL keystores/private keys");

    // Register event channels
    public static final int EVENT_HTTP_REQUEST = registerChannelId("HTTP_REQUEST");
    public static final int EVENT_HTTP_REQUEST_UNHANDLED = registerChannelId("HTTP_REQUEST_UNHANDLED");

    public InetSocketAddress address() {
        return server == null ? null : server.getAddress();
    }

    public int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public com.sun.net.httpserver.HttpServer server() {
        return server;
    }

    // important for port finding when using multiple HttpServers
    protected static final Lock STARTUP_LOCK = new ReentrantLock();

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
            context.info(() -> "[{}] port [{}] stopped", name(), server.getAddress().getPort());
            server = null;
        }
    }

    @Override
    public void start() {
        try {
            server = createHttpServer(context);
            server.setExecutor(GLOBAL_THREAD_POOL);
            server.createContext("/", exchange -> {
                final HttpObject request = new HttpObject(exchange);
                try {
                    final AtomicBoolean internalError = new AtomicBoolean(false);
                    context.sendEventR(EVENT_HTTP_REQUEST, () -> request).peek(setError(internalError)).responseOpt(HttpObject.class).ifPresentOrElse(
                        response -> sendResponse(exchange, request, response),
                        () -> context.sendEventR(EVENT_HTTP_REQUEST_UNHANDLED, () -> request).responseOpt(HttpObject.class).ifPresentOrElse(
                            response -> sendResponse(exchange, request, response),
                            () -> sendResponse(exchange, request, new HttpObject()
                                .statusCode(internalError.get() ? 500 : 404)
                                .bodyT(new LinkedTypeMap().putR("message", internalError.get() ? "Internal Server Error" : "Not Found").putR("timestamp", System.currentTimeMillis()))
                                .contentType(ContentType.APPLICATION_PROBLEM_JSON))
                        )
                    );
                } catch (final Exception e) {
                    context.sendEventR(EVENT_APP_UNHANDLED, () -> new Unhandled(context, request, e)).responseOpt(HttpObject.class).ifPresentOrElse(
                        response -> sendResponse(exchange, request, response),
                        () -> new HttpObject().statusCode(500).body("Internal Server Error".getBytes()).contentType(ContentType.APPLICATION_PROBLEM_JSON)
                    );
                }
            });
            server.start();
            context.info(() -> "[{}] starting on port [{}]", name(), context.get(CONFIG_SERVICE_HTTP_PORT));
            context.asBooleanOpt(CONFIG_SERVICE_HTTP_CLIENT).ifPresent(start -> context.runAwait(new HttpClient()));
        } catch (final IOException e) {
            context.error(e, () -> "[{}] failed to start with port [{}]", name(), context.get(CONFIG_SERVICE_HTTP_PORT));
        }
    }

    @Override
    public void onEvent(final Event event) {
    }

    @Override
    public void configure(final TypeMapI<?> configs, final TypeMapI<?> merged) {

    }

    @Override
    public Object onFailure(final Event error) {
        return null;
    }

    protected void sendResponse(final HttpExchange exchange, final HttpObject request, final HttpObject response) {
        try {
            byte[] body = response.body();
            final int statusCode = response.statusCode() > -1 && response.statusCode() < 600 ? response.statusCode() : 200;
            final Optional<String> encoding = request.acceptEncodings().stream().filter(s -> s.equals("gzip") || s.equals("deflate")).findFirst();
            response.headerMap().asMap(String.class, value -> collectionOf(value, String.class)).forEach((key, value) -> exchange.getResponseHeaders().put(key, value));
            response.computedHeaders(false).forEach((key, value) -> exchange.getResponseHeaders().put(key, value));

            if (encoding.isPresent())
                body = encodeBody(body, encoding.get());
            exchange.getResponseHeaders().put(HttpHeaders.CONTENT_ENCODING, List.of(encoding.orElse("identity")));
            exchange.sendResponseHeaders(statusCode, body.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (final IOException ignored) {
            // Response was already sent
        }
    }

    protected byte[] encodeBody(final byte[] body, final String contentEncoding) {
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            return NanoUtils.encodeGzip(body);
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            return NanoUtils.encodeDeflate(body);
        }
        return body;
    }

    public static int nextFreePort(final int startPort) {
        for (int i = 0; i < 1024; i++) {
            final int port = i + (Math.max(startPort, 1));
            if (!isPortInUse(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Could not find any free port");
    }

    public static boolean isPortInUse(final int portNumber) {
        try {
            new Socket("localhost", portNumber).close();
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    public static Consumer<Event> setError(final AtomicBoolean internalError) {
        return event -> {
            if (event.error() != null) {
                internalError.set(true);
            }
        };
    }
}

