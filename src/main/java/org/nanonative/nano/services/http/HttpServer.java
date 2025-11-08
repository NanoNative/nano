package org.nanonative.nano.services.http;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import com.sun.net.httpserver.HttpExchange;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.file.FileChangeEvent;
import org.nanonative.nano.services.file.FileWatcher;
import org.nanonative.nano.services.http.model.HttpHeaders;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static berlin.yuna.typemap.logic.TypeConverter.collectionOf;
import static org.nanonative.nano.core.model.Context.EVENT_APP_ERROR;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;
import static org.nanonative.nano.services.file.FileWatchRequest.forFilesWithGroup;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_CHANGE;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_UNWATCH;
import static org.nanonative.nano.services.file.FileWatcher.EVENT_FILE_WATCH;
import static org.nanonative.nano.services.http.HttpsHelper.configureHttps;
import static org.nanonative.nano.services.http.HttpsHelper.createDefaultServer;
import static org.nanonative.nano.services.http.HttpsHelper.createHttpsServer;

public class HttpServer extends Service {
    protected com.sun.net.httpserver.HttpServer server;

    // Register configurations
    public static final String CONFIG_SERVICE_HTTP_PORT = registerConfig("app_service_http_port", "Default port for the HTTP service (see " + HttpServer.class.getSimpleName() + ")");
    public static final String CONFIG_SERVICE_HTTP_CLIENT = registerConfig("app_service_http_client", "Boolean if " + HttpClient.class.getSimpleName() + " should start as well");
    public static final String CONFIG_SERVICE_HTTPS_CERTS = registerConfig("app_service_https_certs", "Comma-separated paths to SSL certificates, private keys, or keystores. Can be files or directories.");
    public static final String CONFIG_SERVICE_HTTPS_CERT = registerConfig("app_service_https_cert", "SSL certificate path");
    public static final String CONFIG_SERVICE_HTTPS_CA = registerConfig("app_service_https_ca", "SSL CA certificate path");
    public static final String CONFIG_SERVICE_HTTPS_KEY = registerConfig("app_service_https_key", "SSL private key path");
    public static final String CONFIG_SERVICE_HTTPS_KTS = registerConfig("app_service_https_kts", "SSL keystore path");
    public static final String CONFIG_SERVICE_HTTPS_PASSWORD = registerConfig("app_service_https_password", "Optional password for SSL keystores/private keys");

    // Register event channels
    public static final Channel<HttpObject, HttpObject> EVENT_HTTP_REQUEST = registerChannelId("HTTP_REQUEST", HttpObject.class, HttpObject.class);
    public static final Channel<HttpObject, HttpObject> EVENT_HTTP_REQUEST_UNHANDLED = registerChannelId("HTTP_REQUEST_UNHANDLED", HttpObject.class, HttpObject.class);


    // important for port finding when using multiple HttpServers
    protected static final Lock STARTUP_LOCK = new ReentrantLock();

    public InetSocketAddress address() {
        return server == null ? null : server.getAddress();
    }

    public int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public com.sun.net.httpserver.HttpServer server() {
        return server;
    }

    @Override
    public void start() {
        try {
            STARTUP_LOCK.lock();
            if (hasHttpsConfig(context)) {
                server = createHttpsServer(context);
                configureHttps(context, server);
                refreshCertWatchers(context);
            } else {
                server = createDefaultServer(context);
            }
            server.setExecutor(GLOBAL_THREAD_POOL);
            server.createContext("/", exchange -> {
                final HttpObject request = new HttpObject(exchange);
                final Event<HttpObject, HttpObject> event = context.newEvent(EVENT_HTTP_REQUEST, () -> request);
                try {
                    final AtomicBoolean internalError = new AtomicBoolean(false);
                    event.send().peek(setError(internalError)).responseOpt().ifPresentOrElse(
                            response -> sendResponse(exchange, request, response),
                            () -> context.newEvent(EVENT_HTTP_REQUEST_UNHANDLED, () -> request).send().responseOpt().ifPresentOrElse(
                                    response -> sendResponse(exchange, request, response),
                                    () -> sendResponse(exchange, request, new HttpObject().failure(internalError.get() ? 500 : 404, internalError.get() ? "Internal Server Error" : "Not Found", null))
                            )
                    );
                } catch (final Exception e) {
                    context.newEvent(EVENT_APP_ERROR).payload(() -> event).error(e).containsEvent(true).send();
                    event.responseOpt().ifPresentOrElse(
                            response -> sendResponse(exchange, request, response),
                            () -> sendResponse(exchange, request, new HttpObject().failure(500, "Internal Server Error", null))
                    );
                }
            });
            server.start();
            context.info(() -> "[{}] starting on port [{}]", name(), context.get(CONFIG_SERVICE_HTTP_PORT));
            context.asBooleanOpt(CONFIG_SERVICE_HTTP_CLIENT).map(shouldStart -> context.service(HttpClient.class) == null ? true : null).ifPresent(start -> context.runAwait(new HttpClient()));
        } catch (final IOException e) {
            context.error(e, () -> "[{}] failed to start with port [{}]", name(), context.get(CONFIG_SERVICE_HTTP_PORT));
        } finally {
            STARTUP_LOCK.unlock();
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
            context.info(() -> "[{}] port [{}] stopped", name(), server.getAddress().getPort());
            server = null;
        }
        context.newEvent(EVENT_FILE_UNWATCH, () -> forFilesWithGroup(CONFIG_SERVICE_HTTPS_CERT, Collections.emptyList())).send();
    }


    @Override
    public void onEvent(final Event<?, ?> event) {
        if (server == null)
            event.channel(EVENT_FILE_CHANGE).map(Event::payload).flatMap(FileChangeEvent::group).ifPresent(group -> {
                if (group.equals(CONFIG_SERVICE_HTTPS_CERT))
                    configureHttps(context, server);
            });
    }

    @Override
    public void configure(final TypeMapI<?> configs, final TypeMapI<?> merged) {
        if (hasHttpsConfig(merged) && server != null) {
            context.putAll(configs);
            refreshCertWatchers(context);
            configureHttps(context, server);
        }
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return null;
    }

    protected static boolean hasHttpsConfig(final Map<?, ?> config) {
        return config.containsKey(CONFIG_SERVICE_HTTPS_CERT)
                || config.containsKey(CONFIG_SERVICE_HTTPS_KEY)
                || config.containsKey(CONFIG_SERVICE_HTTPS_CA)
                || config.containsKey(CONFIG_SERVICE_HTTPS_KTS);
    }

    protected void refreshCertWatchers(final Context context) {
        context.newEvent(EVENT_FILE_WATCH, () -> forFilesWithGroup(CONFIG_SERVICE_HTTPS_CERT, Stream.of(
                context.asPath(CONFIG_SERVICE_HTTPS_CERT),
                context.asPath(CONFIG_SERVICE_HTTPS_KEY),
                context.asPath(CONFIG_SERVICE_HTTPS_CA),
                context.asPath(CONFIG_SERVICE_HTTPS_KTS)
        ).filter(Objects::nonNull).toList())).send();
    }

    protected void sendResponse(final HttpExchange exchange, final HttpObject request, final HttpObject response) {
        try {
            byte[] body = response.body();
            final int statusCode = response.statusCode() > -1 && response.statusCode() < 600 ? response.statusCode() : 200;
            final Optional<String> encoding = request.acceptEncodings().stream().filter(s -> s.equals("gzip") || s.equals("deflate")).findFirst();
            response.headerMap().remove("#throwable#");
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

    public static Consumer<Event<HttpObject, HttpObject>> setError(final AtomicBoolean internalError) {
        return event -> {
            if (event.error() != null) {
                internalError.set(true);
            }
        };
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
                .putR("name", port())
                .putR("class", this.getClass().getSimpleName())
                .toJson();
    }
}
