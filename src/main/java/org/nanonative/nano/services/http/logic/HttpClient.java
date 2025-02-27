package org.nanonative.nano.services.http.logic;

import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.net.http.HttpClient.Redirect.NEVER;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.util.Optional.ofNullable;
import static org.nanonative.nano.core.model.NanoThread.GLOBAL_THREAD_POOL;
import static org.nanonative.nano.services.http.HttpService.CONFIG_HTTP_CLIENT_CON_TIMEOUT_MS;
import static org.nanonative.nano.services.http.HttpService.CONFIG_HTTP_CLIENT_FOLLOW_REDIRECTS;
import static org.nanonative.nano.services.http.HttpService.CONFIG_HTTP_CLIENT_MAX_RETRIES;
import static org.nanonative.nano.services.http.HttpService.CONFIG_HTTP_CLIENT_READ_TIMEOUT_MS;
import static org.nanonative.nano.services.http.HttpService.CONFIG_HTTP_CLIENT_VERSION;

public class HttpClient {

    protected final Context context;
    protected final java.net.http.HttpClient client;
    protected final int retries;
    protected final int readTimeoutMs;

    /**
     * Constructs a new {@link HttpClient} with default settings.
     */
    public HttpClient() {
        this(null, null);
    }

    /**
     * Constructs a new {@link HttpClient} with the provided context.
     *
     * @param context the context to use for configuration
     */
    public HttpClient(final Context context) {
        this(context, null);
    }

    /**
     * Constructs a new {@link HttpClient} with the optional provided context and custom {@link java.net.http.HttpClient}.
     *
     * @param context the context to use for configuration
     * @param client  the custom HttpClient instance to use
     */
    @SuppressWarnings("java:S3358") // Ternary operator should not be nested
    public HttpClient(final Context context, final java.net.http.HttpClient client) {
        this.context = context;
        this.client = client != null ? client : java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(ofNullable(context).map(ctx -> ctx.asLong(CONFIG_HTTP_CLIENT_CON_TIMEOUT_MS)).orElse(5000L)))
            .followRedirects(ofNullable(context).map(ctx -> ctx.asBoolean(CONFIG_HTTP_CLIENT_FOLLOW_REDIRECTS)).orElse(true) ? ALWAYS : NEVER)
            .version(ofNullable(context).map(ctx -> ctx.as(java.net.http.HttpClient.Version.class, CONFIG_HTTP_CLIENT_VERSION)).orElse(HTTP_2))
            .executor(GLOBAL_THREAD_POOL)
            .build();
        retries = ofNullable(context).map(ctx -> ctx.asInt(CONFIG_HTTP_CLIENT_MAX_RETRIES)).orElse(3);
        readTimeoutMs = ofNullable(context).map(ctx -> ctx.asInt(CONFIG_HTTP_CLIENT_READ_TIMEOUT_MS)).orElse(10000);
    }

    /**
     * Returns the context used for configuring this {@link HttpClient}.
     *
     * @return the context used for configuring this {@link HttpClient}
     */
    public Context context() {
        return context;
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
    public int readTimeoutMs() {
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

    protected HttpObject send(final int attempt, final HttpRequest request, final HttpObject response, final Consumer<HttpObject> callback) {
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

    @Override
    public String toString() {
        return "HttpClient{" +
            "version=" + version() +
            ", retries=" + retries +
            ", followRedirects=" + followRedirects() +
            ", readTimeoutMs=" + readTimeoutMs +
            ", connectionTimeoutMs=" + connectionTimeoutMs() +
            '}';
    }
}
