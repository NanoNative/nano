package org.nanonative.nano.services.http;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Consumer;

import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.net.http.HttpClient.Redirect.NEVER;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.helper.event.EventChannelRegister.registerChannelId;

public class HttpClient extends Service {

    public static final String CONFIG_HTTP_CLIENT_VERSION = registerConfig("app_service_http_version", "HTTP client version 1 or 2 (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_MAX_RETRIES = registerConfig("app_service_http_max_retries", "Maximum number of retries for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_CON_TIMEOUT_MS = registerConfig("app_service_http_con_timeoutMs", "Connection timeout in milliseconds for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_READ_TIMEOUT_MS = registerConfig("app_service_http_read_timeoutMs", "Read timeout in milliseconds for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_FOLLOW_REDIRECTS = registerConfig("app_service_http_follow_redirects", "Follow redirects for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");
    public static final String CONFIG_HTTP_CLIENT_TRUST_ALL = registerConfig("app_service_http_trust_all", "Trust all certificates for the HTTP client (see " + HttpClient.class.getSimpleName() + ")");

    public static final int EVENT_SEND_HTTP = registerChannelId("SEND_HTTP");

    protected java.net.http.HttpClient client;
    protected int retries = 3;
    protected long readTimeoutMs = 10000;

    @Override
    public void start() {
        final Builder config = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(context.asLongOpt(CONFIG_HTTP_CLIENT_CON_TIMEOUT_MS).orElse(5000L)))
            .followRedirects(context.asBooleanOpt(CONFIG_HTTP_CLIENT_FOLLOW_REDIRECTS).orElse(true) ? ALWAYS : NEVER)
            .version(context.asOpt(java.net.http.HttpClient.Version.class, CONFIG_HTTP_CLIENT_VERSION).orElse(HTTP_2))
            .executor(GLOBAL_THREAD_POOL);
        if( context.asBooleanOpt(CONFIG_HTTP_CLIENT_TRUST_ALL).orElse(false)) {
            config.sslContext(createTrustedSslContext());
        }
        client = config.build();
    }

    @Override
    public void stop() {
        client.close();
        client = null;
    }

    @Override
    public Object onFailure(final Event error) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(final Event event) {
        event.ifPresentResp(EVENT_SEND_HTTP, HttpRequest.class, httpRequest -> send(httpRequest, event.as(Consumer.class, "callback")));
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        changes.asIntOpt(CONFIG_HTTP_CLIENT_MAX_RETRIES).ifPresent(value -> retries = value);
        changes.asIntOpt(CONFIG_HTTP_CLIENT_READ_TIMEOUT_MS).ifPresent(value -> readTimeoutMs = value);
    }

    @Override
    public String toString() {
        return new LinkedTypeMap()
            .putR("version", version())
            .putR("retries", retries)
            .putR("followRedirects", followRedirects())
            .putR("readTimeoutMs", readTimeoutMs)
            .putR("connectionTimeoutMs", connectionTimeoutMs())
            .toJson();
    }

    /**
     * Sends an HTTP request using the provided {@link HttpObject} or {@link HttpRequest}.
     * For async processing, use the {@link HttpClient#send(HttpRequest, Consumer)} method.
     *
     * @param request the {@link HttpObject} or {@link HttpRequest} representing the HTTP request to send
     * @return the response as an {@link HttpObject}
     */
    public HttpObject send(final HttpRequest request) {
        return send(request, null);
    }

    /**
     * Sends an HTTP request using the provided {@link HttpObject} or {@link HttpRequest}.
     * <b>If a response listener is provided, it processes the response asynchronously.</b>
     *
     * @param request  the {@link HttpObject} or {@link HttpRequest} representing the HTTP request to send
     * @param callback an optional consumer to process the response asynchronously
     * @return the response as an {@link HttpObject}
     */
    public HttpObject send(final HttpRequest request, final Consumer<HttpObject> callback) {
        if (request instanceof final HttpObject httpObject)
            httpObject.timeout(readTimeoutMs);
        return request != null ? send(0, request, new HttpObject(), callback) : new HttpObject().failure(400, new IllegalArgumentException("Invalid request [null]"));
    }

    /**
     * Returns the number of retries configured for this {@link HttpClient}.
     *
     * @return the number of retries
     */
    public int retries() {
        return retries;
    }

    /**
     * Returns whether this {@link HttpClient} follows redirects.
     *
     * @return {@code true} if redirects are followed, {@code false} otherwise
     */
    public boolean followRedirects() {
        return ALWAYS.equals(client.followRedirects());
    }

    /**
     * Returns the read timeout in milliseconds configured for this {@link HttpClient}.
     *
     * @return the read timeout in milliseconds
     */
    public long readTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * Returns the connection timeout in milliseconds configured for this {@link HttpClient}.
     *
     * @return the connection timeout in milliseconds
     */
    public long connectionTimeoutMs() {
        return client.connectTimeout().map(Duration::toMillis).orElse(-1L);
    }

    /**
     * Returns the {@link java.net.http.HttpClient.Version} used by this {@link HttpClient}.
     *
     * @return the {@link java.net.http.HttpClient.Version}
     */
    public java.net.http.HttpClient.Version version() {
        return client.version();
    }

    public java.net.http.HttpClient client() {
        return client;
    }

    protected HttpObject send(final int attempt, final HttpRequest request, final HttpObject response, final Consumer<HttpObject> callback) {
        if (client == null)
            configure(context);
        try {
            if (callback == null) {
                return responseOf(client.send(request, HttpResponse.BodyHandlers.ofByteArray()), response);
            } else {
                client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(httpResponse -> responseOf(httpResponse, response)).thenRun(() -> callback.accept(response));
            }
        } catch (final IOException e) {
            return circuitBreaker(attempt, request, response, callback, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            return response.path(request.uri().toString()).statusCode(400).failure(-1, e);
        }
        return response;
    }

    protected HttpObject responseOf(final HttpResponse<byte[]> httpResponse, final HttpObject response) {
        final HttpObject result = response
            .statusCode(httpResponse.statusCode())
            .methodType(httpResponse.request().method())
            .path(httpResponse.uri().getPath())
            .headerMap(httpResponse.headers().map());
        return result.body(httpResponse.body());
    }

    /**
     * Implements a circuit breaker pattern to handle retries for HTTP requests in case of failures.
     * This method attempts to resend the request after a delay that increases exponentially with the number of attempts.
     * If the maximum number of retries is reached, it logs the failure and stops retrying.
     *
     * @param attempt   The current retry attempt number.
     * @param request   The {@link HttpObject} representing the original HTTP request.
     * @param response  The {@link HttpObject} to populate with the response upon successful request completion.
     * @param throwable The {@link Throwable} that triggered the need for a retry.
     * @return A modified {@link HttpObject} containing the result of the retry attempts. If all retries are exhausted without success,
     * it returns the {@link HttpObject} populated with the failure information.
     */
    protected HttpObject circuitBreaker(final int attempt, final HttpRequest request, final HttpObject response, final Consumer<HttpObject> callback, final Throwable throwable) {
        if (attempt < retries) {
            try {
                Thread.sleep((long) Math.pow(2, attempt) * 256);
                return send(attempt + 1, request, response, callback);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                return response.path(request.uri().toString()).failure(-99, ie);
            }
        }
        return response.path(request.uri().toString()).failure(-1, throwable);
    }

    protected static SSLContext createTrustedSslContext() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            final SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build trust-all HttpClient", e);
        }
    }

}
