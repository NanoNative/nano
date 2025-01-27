package org.nanonative.nano.services.http;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.Type;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.core.model.Unhandled;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.config.ConfigRegister;
import org.nanonative.nano.helper.event.EventChannelRegister;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.logic.HttpClient;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpHeaders;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static berlin.yuna.typemap.logic.TypeConverter.collectionOf;

public class HttpService extends Service {
    protected HttpServer server;
    protected Context context;

    // Register configurations
    public static final String CONFIG_SERVICE_HTTP_PORT = ConfigRegister.registerConfig("app_service_http_port", "Default port for the HTTP service (see " + HttpService.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_VERSION = ConfigRegister.registerConfig("app_service_http_version", "HTTP client version 1 or 2 (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_MAX_RETRIES = ConfigRegister.registerConfig("app_service_http_max_retries", "Maximum number of retries for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_CON_TIMEOUT_MS = ConfigRegister.registerConfig("app_service_http_con_timeoutMs", "Connection timeout in milliseconds for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_READ_TIMEOUT_MS = ConfigRegister.registerConfig("app_service_http_read_timeoutMs", "Read timeout in milliseconds for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_FOLLOW_REDIRECTS = ConfigRegister.registerConfig("app_service_http_follow_redirects", "Follow redirects for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");

    // Register event channels
    public static final int EVENT_HTTP_REQUEST = EventChannelRegister.registerChannelId("HTTP_REQUEST");
    public static final int EVENT_HTTP_REQUEST_UNHANDLED = EventChannelRegister.registerChannelId("HTTP_REQUEST_UNHANDLED");

    public HttpService() {
        super(null, false);
    }

    public InetSocketAddress address() {
        return server == null ? null : server.getAddress();
    }

    public int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public HttpServer server() {
        return server;
    }

    // important for port finding when using multiple HttpServers
    protected static final Lock STARTUP_LOCK = new ReentrantLock();

    @Override
    public void stop(final Supplier<Context> contextSub) {
        isReady.set(true, false, state -> {
            server.stop(0);
            logger.info(() -> "[{}] port [{}] stopped", name(), (server == null ? null : server.getAddress().getPort()));
            server = null;
        });
    }

    @Override
    public void start(final Supplier<Context> contextSub) {
        isReady.set(false, true, state -> {
            context = contextSub.get().newContext(HttpService.class);
            STARTUP_LOCK.lock();
            final int port = context.asIntOpt(CONFIG_SERVICE_HTTP_PORT).filter(p -> p > 0).orElseGet(() -> nextFreePort(8080));
            context.put(CONFIG_SERVICE_HTTP_PORT, port);
            handleHttps(context);
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.setExecutor(context.nano().threadPool());
                server.createContext("/", exchange -> {
                    final HttpObject request = new HttpObject(exchange);
                    try {
                        final AtomicBoolean internalError = new AtomicBoolean(false);
                        context.sendEventReturn(EVENT_HTTP_REQUEST, request).peek(setError(internalError)).responseOpt(HttpObject.class).ifPresentOrElse(
                            response -> sendResponse(exchange, request, response),
                            () -> context.sendEventReturn(EVENT_HTTP_REQUEST_UNHANDLED, request).responseOpt(HttpObject.class).ifPresentOrElse(
                                response -> sendResponse(exchange, request, response),
                                () -> sendResponse(exchange, request, new HttpObject()
                                    .statusCode(internalError.get() ? 500 : 404)
                                    .bodyT(new LinkedTypeMap().putR("message", internalError.get() ? "Internal Server Error" : "Not Found").putR("timestamp", System.currentTimeMillis()))
                                    .contentType(ContentType.APPLICATION_PROBLEM_JSON))
                            )
                        );
                    } catch (final Exception e) {
                        context.sendEventReturn(Context.EVENT_APP_UNHANDLED, new Unhandled(context, request, e)).responseOpt(HttpObject.class).ifPresentOrElse(
                            response -> sendResponse(exchange, request, response),
                            () -> new HttpObject().statusCode(500).body("Internal Server Error".getBytes()).contentType(ContentType.APPLICATION_PROBLEM_JSON)
                        );
                    }
                });
                server.start();
                logger.info(() -> "[{}] starting on port [{}]", name(), port);
            } catch (final IOException e) {
                logger.error(e, () -> "[{}] failed to start with port [{}]", name(), port);
            } finally {
                STARTUP_LOCK.unlock();
            }
        });
    }

    @Override
    public void onEvent(final Event event) {
        event.ifPresent(EVENT_HTTP_REQUEST, HttpRequest.class, request -> {
            // Ignore incoming requests
            if (request instanceof final HttpObject httpObject && httpObject.exchange() != null)
                return;
            event.response(((HttpClient) context.computeIfAbsent(HttpObject.CONTEXT_HTTP_CLIENT_KEY, value -> new HttpClient())).send(request));
        });
        super.onEvent(event);
    }

    private static void handleHttps(final Context context) {
        //TODO: add option for HTTPS
        //TODO: handle certificates
        final Type<String> crt = context.asStringOpt(String.class, "app.https.crt.path");
        final Type<String> key = context.asStringOpt(String.class, "app.https.key.path");
        if (crt.isPresent() && key.isPresent()) {
//            // Load the certificate
//            CertificateFactory cf = CertificateFactory.getInstance("X.509");
//            X509Certificate cert = (X509Certificate) cf.generateCertificate(new FileInputStream(crtFilePath));
//
//            // Load the private key
//            final byte[] keyBytes = Files.readAllBytes(Paths.get(keyFilePath));
//            final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
//            KeyFactory kf = KeyFactory.getInstance("RSA"); // TODO: TRY & ERROR loop for all Algorithm
//            final PrivateKey privateKey = kf.generatePrivate(spec);
//
//            // Create a keystore
//            final KeyStore keyStore = KeyStore.getInstance("JKS");
//            keyStore.load(null);
//            keyStore.setCertificateEntry("cert", cert);
//            keyStore.setKeyEntry("key", privateKey, "password".toCharArray(), new Certificate[]{cert});
//
//            // Initialize the SSL context
//            final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//            kmf.init(keyStore, "password".toCharArray());
//            final SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(kmf.getKeyManagers(), null, null);
//
//            // Set up the HTTPS server
//            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
//            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
//            this.server = httpsServer;
        }
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

