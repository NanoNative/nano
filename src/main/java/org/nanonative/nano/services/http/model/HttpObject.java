package org.nanonative.nano.services.http.model;

import berlin.yuna.typemap.logic.JsonDecoder;
import berlin.yuna.typemap.logic.XmlDecoder;
import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeInfo;
import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMap;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.NanoUtils;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.logic.HttpClient;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.Inflater;

import static berlin.yuna.typemap.logic.TypeConverter.collectionOf;
import static berlin.yuna.typemap.logic.TypeConverter.convertObj;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.zip.GZIPInputStream.GZIP_MAGIC;
import static org.nanonative.nano.helper.NanoUtils.hasText;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT_ENCODING;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT_LANGUAGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.nanonative.nano.services.http.model.HttpHeaders.CACHE_CONTROL;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONNECTION;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_ENCODING;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_LENGTH;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_RANGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_TYPE;
import static org.nanonative.nano.services.http.model.HttpHeaders.DATE;
import static org.nanonative.nano.services.http.model.HttpHeaders.HOST;
import static org.nanonative.nano.services.http.model.HttpHeaders.ORIGIN;
import static org.nanonative.nano.services.http.model.HttpHeaders.RANGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.TRANSFER_ENCODING;
import static org.nanonative.nano.services.http.model.HttpHeaders.USER_AGENT;
import static org.nanonative.nano.services.http.model.HttpHeaders.VARY;

/**
 * Represents an HTTP request and response object within a server handling context.
 * This class provides methods to manage HTTP details such as headers, body, and status code,
 * as well as utilities to check request types and content in a fluent and chaining API.
 */
@SuppressWarnings("java:S2386") // Mutable fields should not be "public static"
public class HttpObject extends HttpRequest {

    // lazy loaded fields
    protected HttpMethod method;
    protected String path;
    protected byte[] body;
    protected TypeMap headers;
    protected TypeMap queryParams;
    protected TypeMap pathParams;
    protected int statusCode = -1;
    protected Long timeoutMs;
    protected final HttpExchange exchange;

    // common modifiable fields
    public static final String HTTP_EXCEPTION_HEADER = "#throwable#";
    public static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
    public static final String[] USER_AGENT_BROWSERS = {"chrome", "firefox", "safari", "opera", "edge", "ie", "trident", "vivaldi", "browser", "mozilla", "webkit"};
    public static final String[] USER_AGENT_MOBILE = {"mobile", "ios", "ipad", "ipod", "htc", "nokia", "wii", "psp", "windows phone", "blackberry", "webos", "opera mini", "opera mobi", "kindle", "silk", "puffin", "ucbrowser", "ucweb", "baidubrowser", "baiduboxapp", "samsungbrowser", "miuibrowser", "miuib"};
    public static final String CONTEXT_HTTP_CLIENT_KEY = "app_core_context_http_client";
    public static final List<String> JAVA_MANAGED_HEADERS = List.of(CONTENT_LENGTH, CONNECTION, HOST, TRANSFER_ENCODING);

    /**
     * Constructs a new {@link HttpObject} from a specified {@link HttpExchange}.
     * Initializes headers, method, and path.
     * The {@link HttpExchange} is lazy loaded. Methods like {@link HttpObject#body()} will trigger the loading of the request body.
     *
     * @param exchange the HttpExchange containing the request details.
     */
    public HttpObject(final HttpExchange exchange) {
        this.exchange = exchange;
        if (exchange != null) {
            path(exchange.getRequestURI().getPath());
            methodType(exchange.getRequestMethod());
            headerMap(exchange.getRequestHeaders());
        }
    }

    /**
     * Constructs a {@link HttpObject}.
     */
    public HttpObject() {
        this.exchange = null;
    }

    /**
     * Returns the current HTTP method of this object.
     *
     * @return the current {@link HttpMethod}.
     */
    public HttpMethod methodType() {
        return method;
    }

    /**
     * Sets the HTTP method for this object.
     *
     * @param method the HTTP method as a string. <code>null</code> if method is not one of {@link HttpMethod}.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject methodType(final String method) {
        return methodType(HttpMethod.httpMethodOf(method));
    }

    /**
     * Sets the HTTP method for this object.
     *
     * @param method the HTTP method as an enum {@link HttpMethod}.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject methodType(final HttpMethod method) {
        this.method = method;
        return this;
    }

    /**
     * Retrieves the first content types specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return the primary {@link ContentType} of the request, or {@code null} if no content type is set.
     */
    public ContentType contentType() {
        return contentTypes().getFirst();
    }

    /**
     * Retrieves a list of all content types specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return a list of {@link ContentType} objects representing each content type specified.
     */
    public List<ContentType> contentTypes() {
        final List<ContentType> contentTypes = splitHeaderValue(headerMap().asList(String.class, CONTENT_TYPE), ContentType::fromValue);
        return contentTypes.isEmpty() ? List.of(guessContentType(this, body())) : contentTypes;
    }

    /**
     * Sets the {@link HttpHeaders#CONTENT_TYPE} header without specifying a {@link Charset}.
     *
     * @param contentType array of content type strings to be set in the {@link HttpHeaders#CONTENT_TYPE} header.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject contentType(final String... contentType) {
        return contentType(null, contentType);
    }

    /**
     * Sets the {@link HttpHeaders#CONTENT_TYPE} header without specifying a {@link Charset}.
     *
     * @param contentType array of content type strings to be set in the {@link HttpHeaders#CONTENT_TYPE} header.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject contentType(final ContentType... contentType) {
        return contentType(null, contentType);
    }

    /**
     * Sets the {@link HttpHeaders#CONTENT_TYPE} header with specifying a {@link Charset}.
     *
     * @param charset     the {@link Charset} to set for body encoding.
     * @param contentType array of content type strings to be set in the {@link HttpHeaders#CONTENT_TYPE} header.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject contentType(final Charset charset, final String... contentType) {
        return contentType(charset, Arrays.stream(contentType).map(ContentType::fromValue).filter(Objects::nonNull).toArray(ContentType[]::new));
    }

    /**
     * Sets the {@link HttpHeaders#CONTENT_TYPE} header with specifying a {@link Charset}.
     *
     * @param charset     the {@link Charset} to set for body encoding.
     * @param contentType array of content type strings to be set in the {@link HttpHeaders#CONTENT_TYPE} header.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject contentType(final Charset charset, final ContentType... contentType) {
        headerMap().put(CONTENT_TYPE, Arrays.stream(contentType)
            .filter(Objects::nonNull)
            .map(ContentType::value)
            .collect(Collectors.joining(", ")) + (charset == null ? "" : "; charset=" + charset.name()));
        return this;
    }

    public ContentType accept() {
        final List<ContentType> result = accepts();
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<ContentType> accepts() {
        return splitHeaderValue(headerMap().asList(String.class, ACCEPT), ContentType::fromValue);
    }

    public HttpObject accept(final String... contentType) {
        headerMap().put(HttpHeaders.ACCEPT, Arrays.stream(contentType)
            .map(ContentType::fromValue)
            .filter(Objects::nonNull)
            .map(ContentType::value)
            .collect(Collectors.joining(", ")));
        return this;
    }

    public HttpObject accept(final ContentType... contentType) {
        headerMap().put(HttpHeaders.ACCEPT, Arrays.stream(contentType)
            .filter(Objects::nonNull)
            .map(ContentType::value)
            .collect(Collectors.joining(", ")));
        return this;
    }

    public boolean hasAccept(final String... contentTypes) {
        final List<ContentType> result = accepts();
        return Arrays.stream(contentTypes).map(ContentType::fromValue).allMatch(result::contains);
    }

    public boolean hasAccept(final ContentType... contentTypes) {
        final List<ContentType> result = accepts();
        return Arrays.stream(contentTypes).allMatch(result::contains);
    }

    public String acceptEncoding() {
        final List<String> result = acceptEncodings();
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<String> acceptEncodings() {
        return splitHeaderValue(headerMap().asList(String.class, ACCEPT_ENCODING), v -> v);
    }

    public boolean hasAcceptEncoding(final String... encodings) {
        final List<String> result = splitHeaderValue(headerMap().asList(String.class, ACCEPT_ENCODING), v -> v);
        return Arrays.stream(encodings).allMatch(result::contains);
    }

    public String contentEncoding() {
        final List<String> result = acceptEncodings();
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<String> contentEncodings() {
        return splitHeaderValue(headerMap().asList(String.class, CONTENT_ENCODING), v -> v);
    }

    public boolean hasContentEncoding(final String... encodings) {
        final List<String> result = splitHeaderValue(headerMap().asList(String.class, CONTENT_ENCODING), v -> v);
        return Arrays.stream(encodings).allMatch(result::contains);
    }

    public Locale acceptLanguage() {
        return acceptLanguages().getFirst();
    }

    public List<Locale> acceptLanguages() {
        final List<Locale> result = splitHeaderValue(headerMap().asList(String.class, ACCEPT_LANGUAGE), Locale::forLanguageTag);
        return result.isEmpty() ? List.of(Locale.ENGLISH) : result;
    }

    /**
     * Sets the accepted languages based on a list where the order indicates priority.
     * The first language in the list has the highest priority, automatically assigning descending priorities.
     *
     * @param locales Ordered array of {@link Locale} representing the preferred languages, from most to least preferred.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject acceptLanguages(final Locale... locales) {
        if (locales == null || locales.length == 0) {
            headerMap().remove(HttpHeaders.ACCEPT_LANGUAGE);
        } else {
            final String acceptLanguageValue = IntStream.range(0, locales.length)
                .mapToObj(i -> locales[i].toLanguageTag() + ";q=" + Math.max(0.1, 1.1 - ((i + 1d) / 10)))
                .collect(Collectors.joining(", "));
            headerMap().put(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageValue);
        }
        return this;
    }

    /**
     * Retrieves the current path of the {@link HttpObject} without last '/'.
     *
     * @return the request path.
     */
    public String path() {
        return path;
    }

    /**
     * Sets the path for this {@link HttpObject}.
     * This method also parses and sets the query parameters if they are included in the path.
     *
     * @param path the path to set, which may include query parameters.
     * @return this HttpObject to allow method chaining.
     */
    public HttpObject path(final String path) {
        final String[] parts = path == null ? new String[0] : NanoUtils.split(path, "?");
        this.path = parts.length > 0 ? removeLast(parts[0], "/") : null;
        if (parts.length > 1) {
            queryParams = queryParamsOf(parts[1]);
        }
        return this;
    }

    /**
     * Returns a string representation of the {@link HttpObject#body()}, decoded using the {@link Charset} from {@link HttpObject#encoding()} specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return the body as a string.
     */
    public String bodyAsString() {
        return new String(body(), encoding());
    }

    /**
     * Returns a string representation of the {@link HttpObject#body()}, decoded using the {@link Charset} from {@link HttpObject#encoding()} specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return the body as a json.
     */
    @SuppressWarnings("java:S1452") // generic wildcard type
    public TypeInfo<?> bodyAsJson() {
        return JsonDecoder.jsonTypeOf(bodyAsString());
    }

    /**
     * Returns a string representation of the {@link HttpObject#body()}, decoded using the {@link Charset} from {@link HttpObject#encoding()} specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return the body as a xml.
     */
    @SuppressWarnings("java:S1452") // generic wildcard type
    public TypeInfo<?> bodyAsXml() {return XmlDecoder.xmlTypeOf(bodyAsString());}

    /**
     * Returns a string representation of the body, decoded using the {@link Charset} from {@link HttpObject#encoding()} specified in the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return the body as a xml.
     */
    public byte[] body() {
        if (body == null && exchange != null) {
            try (final InputStream bodyStream = exchange.getRequestBody()) {
                body = bodyStream.readAllBytes();
            } catch (final Exception ignored) {
                // ignored
            }
        }
        if (body == null)
            body(new byte[0]);
        return body;
    }

    /**
     * Sets the {@link HttpObject#body()} from a {@link TypeInfo} object, encoding it into JSON format using the {@link Charset} from {@link HttpObject#encoding()}.
     *
     * @param body the {@link TypeInfo} representing the body to be set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject bodyT(final TypeInfo<?> body) {
        return body(body.toJson().getBytes(encoding()));
    }

    /**
     * Sets the {@link HttpObject#body()} from a {@link Collection} object, encoding it into JSON format using the {@link Charset} from {@link HttpObject#encoding()}.
     *
     * @param body the {@link Collection} representing the body to be set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject body(final Collection<?> body) {
        return bodyT((body instanceof final TypeInfo<?> info ? info : new TypeList(body)));
    }

    /**
     * Sets the {@link HttpObject#body()} from a {@link Map} object, encoding it into JSON format using the {@link Charset} from {@link HttpObject#encoding()}.
     *
     * @param body the {@link Map} representing the body to be set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject body(final Map<?, ?> body) {
        return bodyT((body instanceof final TypeInfo<?> info ? info : new LinkedTypeMap(body)));
    }

    /**
     * Sets the {@link HttpObject#body()} from a {@link TypeInfo} object, encoding it into bytes using the {@link Charset} from {@link HttpObject#encoding()}.
     *
     * @param body the String representing the body to be set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject body(final String body) {
        return body(body.getBytes(encoding()));
    }

    /**
     * Sets the {@link HttpObject#body()} from a {@link TypeInfo} object.
     *
     * @param body the String representing the body to be set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject body(final byte[] body) {
        if (body.length > 2) {
            if ((body[0] & 0xFF) == (GZIP_MAGIC & 0xFF) && (body[1] & 0xFF) == ((GZIP_MAGIC >> 8) & 0xFF)) {
                this.body = NanoUtils.decodeGzip(body);
            } else if (isZipCompressed(body)) {
                this.body = NanoUtils.decodeZip(body);
            } else if (isDeflateCompressed(body)) {
                this.body = NanoUtils.decodeDeflate(body);
            } else {
                this.body = body;
            }
        } else {
            this.body = body;
        }
        return this;
    }

    /**
     * Retrieves all query parameters from the current {@link HttpObject}.
     *
     * @return a {@link TypeMap} containing all the query parameters.
     */
    public TypeMap queryParams() {
        if (queryParams == null && exchange != null) {
            queryParams = ofNullable(fromExchange(httpExchange -> queryParamsOf(httpExchange.getRequestURI().getQuery()))).orElseGet(TypeMap::new);
        }
        if (queryParams == null) {
            queryParams = new TypeMap();
        }
        return queryParams;

    }

    /**
     * Checks if a specific query parameter exists in the {@link HttpObject}.
     *
     * @param key the query parameter key to check.
     * @return {@code true} if the parameter exists, {@code false} otherwise.
     */
    public boolean containsQueryParam(final String key) {
        return queryParams().containsKey(key);
    }

    /**
     * Retrieves the value of a specified query parameter.
     *
     * @param key the key of the query parameter to retrieve.
     * @return the value of the parameter as a String, or {@code null} if the parameter does not exist.
     */
    public String queryParam(final String key) {
        return queryParams().asString(key);
    }

    /**
     * <p>
     * Checks if the current {@link HttpObject#path()} matches a specified expression.
     * The expression may include:
     * <ul>
     *     <li</>Path variables enclosed in curly braces ({}), e.g., /users/{userId}.</li>
     *     <li</>Asterisks ({@literal *}) to match any single path segment, e.g., /users/{@literal *}/profile</li>
     *     <li</>Double asterisks (**) to match any number of path segments, e.g., /users/**..</li>
     * </ul>
     * </p><p>
     * Matching rules:
     * <ul>
     *     <li</>Exact match: Each part of the path must match the corresponding part of the expression.</li>
     *     <li</>Single asterisk ({@literal *}): Matches any single path segment.</li>
     *     <li</>Double asterisk (**): Matches zero or more path segments.</li>
     *     <li</>Path variables: Captures the value of the segment and stores it in {@link HttpObject#pathParams()}.</li>
     * </ul>
     * <lp>
     *
     * @param expression the path expression to match against the current path.
     * @return {@code true} if the current path matches the expression, {@code false} otherwise.
     */
    public boolean pathMatch(final String expression) {
        if (this.path == null || expression == null)
            return false;

        final String[] partsToMatch = NanoUtils.split(removeLast(expression, "/"), "/");
        final String[] parts = NanoUtils.split(this.path, "/");

        pathParams().clear();
        for (int i = 0; i < partsToMatch.length; i++) {
            if ("*".equals(partsToMatch[i]))
                continue;
            if ("**".equals(partsToMatch[i]))
                return true;
            if (parts.length - 1 < i)
                return false;
            if (!partsToMatch[i].equals(parts[i])) {
                if (partsToMatch[i].startsWith("{")) {
                    final String key = partsToMatch[i].substring(1, partsToMatch[i].length() - 1);
                    pathParams.put(key, parts[i]);
                } else {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Retrieves a map of path parameters extracted from the URL.
     *
     * @return a {@link TypeMap} of path parameters.
     */
    public TypeMap pathParams() {
        if (pathParams == null)
            pathParams = new TypeMap();
        return pathParams;
    }

    /**
     * Retrieves the value of a specified path parameter.
     *
     * @param key the key of the path parameter.
     * @return the value of the path parameter, or {@code null} if it does not exist.
     */
    public String pathParam(final String key) {
        return pathParams().asString(key);
    }

    /**
     * Retrieves the value of a specified header.
     *
     * @param key the key of the header to retrieve.
     * @return the value of the header, or {@code null} if the header is not found or {@code key} is {@code null}.
     */
    public String header(final String key) {
        return key == null || headers == null ? null : headers.asString(key.toLowerCase());
    }

    /**
     * Checks if a specified header exists in the {@link HttpObject}.
     *
     * @param key the key of the header to check.
     * @return {@code true} if the header exists, {@code false} otherwise.
     */
    public boolean containsHeader(final String key) {
        return key != null && headers != null && headers.containsKey(key.toLowerCase());
    }

    /**
     * Retrieves the {@link HttpHeaders#USER_AGENT} header from the {@link HttpObject}.
     *
     * @return the user agent string, or {@code null} if not set.
     */
    public String userAgent() {
        return header(HttpHeaders.USER_AGENT);
    }

    /**
     * Sets the {@link HttpHeaders#USER_AGENT} header for the {@link HttpObject}.
     *
     * @param userAgent the user agent string to set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject userAgent(final String userAgent) {
        headerMap().put(HttpHeaders.USER_AGENT, userAgent);
        return this;
    }

    /**
     * Parses and returns any authentication token found in the {@link HttpHeaders#AUTHORIZATION} header.
     * Supports both 'Bearer' and 'Basic' authentication schemes.
     *
     * @return token from the {@link HttpObject#authTokens()}
     * or null if no {@link HttpHeaders#AUTHORIZATION} header is present or the token cannot be parsed.
     */
    public String authToken() {
        return authToken(0);
    }

    /**
     * Parses and returns any authentication token found in the {@link HttpHeaders#AUTHORIZATION} header.
     * Supports both 'Bearer' and 'Basic' authentication schemes.
     *
     * @return token from the {@link HttpObject#authTokens()}
     * or null if no {@link HttpHeaders#AUTHORIZATION} header is present or the token cannot be parsed.
     */
    public String authToken(final int index) {
        final String[] result = authTokens();
        return index < result.length ? result[index] : null;
    }

    /**
     * Parses and returns any authentication token found in the {@link HttpHeaders#AUTHORIZATION} header.
     * Supports both 'Bearer' and 'Basic' authentication schemes.
     *
     * @return an array of strings, where the first element is the token or credentials,
     * or an empty array if no {@link HttpHeaders#AUTHORIZATION} header is present or the token cannot be parsed.
     */
    public String[] authTokens() {
        return ofNullable(header(HttpHeaders.AUTHORIZATION))
            .map(value -> {
                if (value.startsWith("Bearer ")) {
                    return new String[]{value.substring("Bearer ".length())};
                }
                if (value.startsWith("Basic ")) {
                    final String decode = new String(Base64.getDecoder().decode(value.substring("Basic ".length())));
                    return decode.contains(":") ? NanoUtils.split(decode, ":") : new String[]{decode};
                }
                return new String[]{value};
            })
            .orElse(new String[0]);
    }

    /**
     * Returns the associated {@link HttpExchange} for this {@link HttpObject}.
     *
     * @return the {@link HttpExchange}, or {@code null} if not available.
     */
    public HttpExchange exchange() {
        return exchange;
    }

    /**
     * Sets the HTTP status code for the {@link HttpObject}.
     *
     * @param statusCode the HTTP status code to set.
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject statusCode(final int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    /**
     * Retrieves the current HTTP status code for the {@link HttpObject}.
     *
     * @return the HTTP status code.
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the HTTP headers map.
     *
     * @return a {@link TypeMap} containing the headers.
     */
    public TypeMap headerMap() {
        if (headers == null)
            headers = new TypeMap();
        return headers;
    }

    /**
     * <p>
     * Constructs and returns a map of HTTP headers to be included in an HTTP response.
     * This method ensures that essential headers are set, providing a sensible default for headers
     * like Content-Encoding, Connection, Cache-Control, Accept-Encoding, Content-Type, Content-Length, and Date.
     * </p><p>
     * The method sets defaults for managing caching, connection persistence, and content encoding to optimize
     * performance and compliance with HTTP standards. It also dynamically calculates content length and sets the
     * current date and time in GMT format.
     * </p><p>
     *
     * @return A map where keys are header names and values are lists of string representing the header values.
     * This structure supports headers having multiple values.
     * </p><p>
     * Header Details:
     * - {@link HttpHeaders#ACCEPT}: Default "*\/*".
     * - {@link HttpHeaders#CACHE_CONTROL}: Ensures fresh content by specifying "no-cache" and overrides with "max-age=0, private, must-revalidate" for more specific caching rules.
     * - {@link HttpHeaders#ACCEPT_ENCODING}: Lists the acceptable encodings the server can handle, defaults to "gzip, deflate".
     * - {@link HttpHeaders#CONTENT_TYPE}: Determined by {@code contentTypes()} method to set the correct media type of the response.
     * - {@link HttpHeaders#CONTENT_LENGTH}: Calculates the length of the response body to inform the client of the size of the response.
     * - {@link HttpHeaders#DATE}: Sets the current date and time formatted according to RFC 7231.
     * </p>
     */
    public Map<String, List<String>> computedHeaders(final boolean isRequest) {
        final TypeMap result = headerMap();
        if (isRequest) {
            result.putIfAbsent(ACCEPT_ENCODING, "gzip, deflate");
            result.computeIfAbsent(ACCEPT, fallback -> ContentType.WILDCARD.value());
        }
        result.putIfAbsent(CACHE_CONTROL, "no-cache");
        result.computeIfAbsent(CONTENT_TYPE, value -> contentTypes().stream().map(ContentType::value).toList());
        result.computeIfAbsent(CONTENT_LENGTH, value -> this.body().length);
        result.computeIfAbsent(DATE, value -> HTTP_DATE_FORMATTER.format(ZonedDateTime.now().withZoneSameInstant(java.time.ZoneOffset.UTC)));
        result.computeIfAbsent(USER_AGENT, fallback -> NanoUtils.generateNanoName("%s/%s (%s %s)"));
        return result.asMap(String.class, value -> collectionOf(value, String.class));
    }

    /**
     * Sets the HTTP Range header to request only the first byte of the content.
     * This method is typically used to determine the size of the content without downloading it entirely.
     * The server should respond with the Content-Range header which includes the total size of the requested resource.
     *
     * @return this {@link HttpObject} to allow method chaining.
     */
    public HttpObject sizeRequest(final boolean sizeRequest) {
        if (sizeRequest)
            headerMap().put(RANGE, "bytes=0-0");
        if (!sizeRequest)
            headerMap().remove(RANGE);
        return this;
    }

    /**
     * Checks if the Range header has been set to request only the first byte of the content.
     * This method is used to determine if the current request is a size request, which is typically
     * used to fetch the size of the content without fully downloading it.
     *
     * @return true if the Range header is set to "bytes=0-0", indicating a size request; false otherwise.
     */

    public boolean sizeRequest() {
        return "bytes=0-0".equals(headerMap().get(RANGE));
    }

    /**
     * <p>
     * Calculates the size of the HTTP content.
     * This method first attempts to retrieve the content size from the HTTP headers.
     * It checks the {@code Content-Length} header for a direct size declaration.
     * If not present, it tries to extract the size from the {@code Content-Range} header,
     * which is used in responses to range requests.
     * </p><p>
     * If neither header is present or they don't contain valid size information,
     * the method falls back to the length of the body, which is loaded into memory.
     * </p>
     *
     * @return the size of the content as a long. If the size cannot be determined from the headers,
     * it returns the length of the body. If the headers are not set or are invalid, and the body is not loaded,
     * it returns -1.
     */
    public long size() {
        return Math.max(
            body().length,
            headers == null ? -1L : headers.getOpt(String.class, CONTENT_RANGE).map(s -> s.replace("bytes 0-0/", "")).map(s -> convertObj(s, Long.class)).orElse(-1L)
        );
    }

    /**
     * Sets Http headers.
     *
     * @param headers the header keys and values.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject headerMap(final Headers headers) {
        this.headers = convertHeaders(headers);
        return this;
    }

    /**
     * Sets Http headers.
     *
     * @param headers the header keys and values.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject headerMap(final Map<String, ?> headers) {
        this.headers = convertHeaders(headers);
        return this;
    }

    /**
     * Adds or replaces a header in the HTTP headers map.
     *
     * @param key   the header name.
     * @param value the header value.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject header(final String key, final Object value) {
        if (key != null && value != null)
            headerMap().put(key.toLowerCase(), value);
        return this;
    }

    protected TypeMap queryParamsOf(final String query) {
        if (queryParams == null) {
            queryParams = ofNullable(query)
                .map(q -> {
                    final TypeMap result = new TypeMap();
                    Arrays.stream(NanoUtils.split(q, "&"))
                        .map(param -> NanoUtils.split(param, "="))
                        .forEach(keyValue -> {
                            final String key = URLDecoder.decode(keyValue[0], UTF_8);
                            final String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], UTF_8) : "";
                            result.put(key, value);
                        });
                    return result;
                })
                .orElseGet(TypeMap::new);
        }
        return queryParams;
    }

    // ########## HTTP REQUEST HELPERS ##########

    @Override
    public Optional<BodyPublisher> bodyPublisher() {
        return Optional.of(HttpRequest.BodyPublishers.ofByteArray(body()));
    }

    @Override
    public String method() {
        return method == null ? HttpMethod.GET.name() : method.name();
    }

    @Override
    public Optional<Duration> timeout() {
        return timeoutMs == null ? Optional.empty() : Optional.of(Duration.ofMillis(timeoutMs));
    }

    public HttpObject timeout(final long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    @Override
    public boolean expectContinue() {
        return false;
    }

    @Override
    public URI uri() {
        // TODO: with query params?
        return URI.create(path() == null ? "" : path());
    }

    @Override
    public Optional<java.net.http.HttpClient.Version> version() {
        // overwritten by HttpClient
        return Optional.empty();
    }

    @Override
    public java.net.http.HttpHeaders headers() {
        final Map<String, List<String>> map = computedHeaders(true);
        JAVA_MANAGED_HEADERS.forEach(map::remove);
        return java.net.http.HttpHeaders.of(map, (k, v) -> true);
    }

    // ########## NANO EVENT HELPERS ##########

    /**
     * Creates and returns a new instance of {@link HttpObject}.
     * This method is typically used to prepare a fresh response object in the context of HTTP handling.
     * The new instance is completely independent of the current object,
     * meaning it has no initialized fields from the current context.
     *
     * @return a new, empty {@link HttpObject}.
     */
    public HttpObject response() {
        return new HttpObject();
    }

    /**
     * Creates and returns a new instance of {@link HttpObject}.
     * If CORS handling is requested, it delegates to the CORS response method.
     *
     * @return a new {@link HttpObject}, CORS-enabled.
     */
    public HttpObject corsResponse() {
        return response(true);
    }

    /**
     * Creates and returns a new instance of {@link HttpObject}.
     * If CORS handling is requested, it delegates to the CORS response method.
     *
     * @param cors if true, generates a CORS-enabled response.
     * @return a new {@link HttpObject}, optionally CORS-enabled.
     */
    public HttpObject response(final boolean cors) {
        return cors ? corsResponse(null) : new HttpObject();
    }

    /**
     * Creates and returns a new instance of {@link HttpObject} with CORS handling.
     * If the origin is not provided, it defaults to "*" (wildcard).
     *
     * @param origin comma separated list of whitelisted origins, or null for default.
     * @return a new {@link HttpObject} with CORS handling.
     */
    public HttpObject corsResponse(final String origin) {
        return corsResponse(origin, null);
    }

    /**
     * Creates and returns a new instance of {@link HttpObject} with CORS handling.
     * Allows defining the origin and allowed methods.
     *
     * @param origin  comma separated list of whitelisted origins, or null for default.
     * @param methods the allowed HTTP methods, or null to use the current method.
     * @return a new {@link HttpObject} with CORS handling.
     */
    public HttpObject corsResponse(final String origin, final String methods) {
        return corsResponse(origin, methods, null);
    }

    /**
     * Creates and returns a new instance of {@link HttpObject} with CORS handling.
     * Allows defining the origin, methods, and headers.
     *
     * @param origin  comma separated list of whitelisted origins, or null for default.
     * @param methods the allowed HTTP methods, or null to use the current method.
     * @param headers the allowed HTTP headers, or null to default headers.
     * @return a new {@link HttpObject} with CORS handling.
     */
    public HttpObject corsResponse(final String origin, final String methods, final String headers) {
        return corsResponse(origin, methods, headers, -1);
    }

    /**
     * Creates and returns a new instance of {@link HttpObject} with CORS handling.
     * Allows defining the origin, methods, headers, and max age for caching preflight responses.
     *
     * @param origin  comma separated list of whitelisted origins, or null for default.
     * @param methods the allowed HTTP methods, or null to use the current method.
     * @param headers the allowed HTTP headers, or null to default headers.
     * @param maxAge  the max age for caching preflight responses, or -1 for default (86400 seconds).
     * @return a new {@link HttpObject} with CORS handling.
     */
    public HttpObject corsResponse(final String origin, final String methods, final String headers, final int maxAge) {
        return corsResponse(origin, methods, headers, maxAge, false);
    }

    /**
     * Creates and returns a new instance of {@link HttpObject} with CORS handling.
     * Allows defining the origin, methods, headers, max age, and whether to allow credentials.
     *
     * @param origin      comma separated list of whitelisted origins, or null for default.
     * @param methods     the allowed HTTP methods, or null to use the current method.
     * @param headers     the allowed HTTP headers, or null to default headers.
     * @param maxAge      the max age for caching preflight responses, or -1 for default (86400 seconds).
     * @param credentials if true, enables Access-Control-Allow-Credentials header.
     * @return a new {@link HttpObject} with CORS handling.
     */
    @SuppressWarnings("java:S3358")
    public HttpObject corsResponse(
        final String origin,
        final String methods,
        final String headers,
        final int maxAge,
        final boolean credentials
    ) {
        final String resultOrigin = origin(origin, credentials);
        return new HttpObject()
            .header(ACCESS_CONTROL_ALLOW_ORIGIN, resultOrigin)
            .header(ACCESS_CONTROL_ALLOW_METHODS, hasText(methods) ? methods : (headerMap().getOpt(String.class, ACCESS_CONTROL_REQUEST_METHOD).orElse(method())))
            .header(ACCESS_CONTROL_ALLOW_HEADERS, hasText(headers) ? headers : (headerMap().getOpt(String.class, ACCESS_CONTROL_REQUEST_HEADERS).orElse("Content-Type, Accept, Authorization, X-Requested-With")))
            .header(ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(credentials))
            .header(ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge > 0 ? maxAge : 86400))
            .statusCode(resultOrigin.equals("null") ? 403 : (isMethodOptions() ? 204 : 200)) // 403 if origin doesn't match, 204 for preflight, 200 otherwise
            .header(VARY, "Origin"); // Ensure the response is cached based on the Origin header
    }

    /**
     * Retrieves the origin from the current request.
     * If the request does not specify an origin, it defaults to "*".
     *
     * @return the origin of the request, or "*" if no origin is specified.
     */
    public String origin() {
        return origin(null, false);
    }

    /**
     * Determines the value for the {@code Access-Control-Allow-Origin} header.
     * <p>
     * If credentials are allowed, the origin cannot be {@code "*"}. It prioritizes the provided {@code origin}.
     * If no valid origin is provided, it falls back to the {@code ORIGIN} or {@code HOST} headers from the request.
     * Returns {@code "null"} if credentials are used and no origin is found, otherwise returns {@code "*"}.
     * </p>
     *
     * @param origin      comma separated list of whitelisted origins, or null for default.
     * @param credentials if {@code true}, prevents using {@code "*"} as the origin.
     * @return the appropriate origin, or {@code "null"} if credentials are used and no origin is found.
     */
    @SuppressWarnings("java:S3358") // too many parameters
    public String origin(final String origin, final boolean credentials) {
        final String requestOrigin = headerMap().getOpt(String.class, ORIGIN).or(() -> headerMap().getOpt(String.class, HOST)).orElseGet(() -> credentials ? "null" : "*");
        return hasText(origin) && !(credentials && origin.equals("*"))
            ? origin.equals("*")? origin : Arrays.stream(origin.split(",")).map(String::trim).filter(requestOrigin::equals).findFirst().orElse("null")
            : requestOrigin;
    }

    /**
     * Sets the origin header for the current {@link HttpObject}.
     * If the provided origin is null or empty, the origin header is removed.
     *
     * @param origin the origin to set in the headers.
     * @return this {@link HttpObject} for method chaining.
     */
    public HttpObject origin(final String origin) {
        if (hasText(origin)) {
            headerMap().put(ORIGIN, origin);
        } else {
            headerMap().remove(ORIGIN);
        }
        return this;
    }

    // ########## REQUEST / RESPONSE HELPERS ##########

    /**
     * Sends an HTTP response using the current {@link HttpObject} as the response context.
     * This method utilizes the provided {@link Event} to carry the response back to the event handler or processor.
     * The {@link HttpObject} is attached to the event as its response payload, allowing further processing or handling.
     *
     * @param event the event to which this {@link HttpObject} should be attached as a response.
     * @return the event after attaching this {@link HttpObject} as a response, facilitating chaining and further manipulation.
     */
    public Event respond(final Event event) {
        return event.response(this);
    }

    /**
     * Sends an HTTP request using the provided {@link HttpObject}.
     * For async processing, use the {@link HttpObject#send(Context, Consumer)} method.
     *
     * @return the response as an {@link HttpObject}
     */
    public HttpObject send(final Context context) {
        return send(context, null);
    }

    /**
     * Sends an HTTP request using the provided {@link HttpObject}.
     * <b>If a response listener is provided, it processes the response asynchronously.</b>
     *
     * @param callback an optional consumer to process the response asynchronously
     * @return the response as an {@link HttpObject}
     */
    public HttpObject send(final Context context, final Consumer<HttpObject> callback) {
        if (context == null) {
            return null;
        }
        return ((HttpClient) context.computeIfAbsent(CONTEXT_HTTP_CLIENT_KEY, value -> new HttpClient(context))).send(this, callback);
    }

    /**
     * Checks if the HTTP response headers contain an entry indicating an exception or failure.
     * This is typically used to determine if the HTTP operation encountered any errors
     * that were captured and stored in the headers.
     *
     * @return true if an exception or failure is indicated in the headers, false otherwise.
     */
    public boolean hasFailed() {
        return !is2xxSuccessful() || (headers != null && (headers.containsValue(ContentType.APPLICATION_PROBLEM_JSON.value()) || headers.containsValue(ContentType.APPLICATION_PROBLEM_XML.value()) || headers.containsValue(HTTP_EXCEPTION_HEADER)));
    }

    /**
     * Retrieves the failure exception from the HTTP headers, if present.
     * This method directly accesses the headers to pull out a {@link Throwable} object
     * which represents the exception that was encountered during the HTTP transaction.
     *
     * @return the {@link Throwable} that represents the failure, or null if no failure was recorded.
     */
    public Throwable failure() {
        return headers == null ? null : headers.as(Throwable.class, HTTP_EXCEPTION_HEADER);
    }

    public HttpObject successOrElse(final Consumer<HttpObject> onSuccess, final Consumer<HttpObject> onFailure) {
        if (hasFailed()) {
            onFailure.accept(this);
        } else {
            onSuccess.accept(this);
        }
        return this;
    }

    /**
     * Stores a failure exception within the HTTP headers. RFC 7807 To The Rescue!
     * This method allows an exception to be attached to the HTTP object,
     * potentially for logging purposes or for transmitting error information back to a client or server.
     *
     * @param throwable the {@link Throwable} exception to store in the headers.
     * @return this {@link HttpObject} to allow for method chaining.
     */
    public HttpObject failure(final int statusCode, final Throwable throwable) {
        header(HTTP_EXCEPTION_HEADER, throwable);
        header(CONTENT_TYPE, ContentType.APPLICATION_PROBLEM_JSON.value());
        statusCode(statusCode);
        body(new TypeMap()
            .putReturn("id", NanoUtils.generateNanoName("%s_%s_%s_%s").toLowerCase().replace(".", "").replace(" ", "_"))
            .putReturn("type", "https://github.com/nanonative/nano")
            .putReturn("title", ofNullable(throwable.getMessage()).orElse(throwable.getClass().getSimpleName()))
            .putReturn("status", statusCode)
            .putReturn("instance", path())
            .putReturn("detail", convertObj(throwable, String.class))
            .putReturn("timestamp", Instant.now().toEpochMilli())
        );
        return this;
    }

    // ########## NON FUNCTIONAL HELPERS ##########

    /**
     * Determines if the {@link HttpObject} originates from a standard web browser based on the {@link HttpHeaders#USER_AGENT} header.
     * See {@link HttpObject#USER_AGENT_BROWSERS} for the list of browser identifiers.
     *
     * @return {@code true} if the {@link HttpHeaders#USER_AGENT} header contains identifiers typical of desktop browsers, otherwise {@code false}.
     */
    public boolean isFrontendCall() {
        return ofNullable(headers)
            .map(header -> header.asString(HttpHeaders.USER_AGENT))
            .map(String::toLowerCase)
            .filter(agent -> (Stream.of(USER_AGENT_BROWSERS).anyMatch(agent::contains)))
            .isPresent();
    }

    /**
     * Determines if the {@link HttpObject} originates from a mobile device based on the {@link HttpHeaders#USER_AGENT} header.
     * See {@link HttpObject#USER_AGENT_MOBILE} for the list of mobile identifiers.
     *
     * @return {@code true} if the {@link HttpHeaders#USER_AGENT} header contains identifiers typical of mobile devices, otherwise {@code false}.
     */
    public boolean isMobileCall() {
        return ofNullable(headers)
            .map(header -> header.asString(HttpHeaders.USER_AGENT))
            .map(String::toLowerCase)
            .filter(agent -> (Stream.of(USER_AGENT_MOBILE).anyMatch(agent::contains)))
            .isPresent();
    }

    /**
     * Retrieves the host name from the {@link HttpExchange} if available, otherwise extracts it from the {@link HttpHeaders#HOST} header.
     *
     * @return the host name as a string, or {@code null} if it cannot be determined.
     */
    public String host() {
        return ofNullable(fromExchange(httpExchange -> httpExchange.getRemoteAddress().getHostName()))
            .or(() -> ofNullable(headers).map(header -> header.asString(HttpHeaders.HOST)).map(value -> NanoUtils.split(value, ":")[0])).orElse(null);
    }

    /**
     * Retrieves the host port from the {@link HttpExchange} if available, otherwise extracts it from the {@link HttpHeaders#HOST} header.
     *
     * @return the port number as an integer, or -1 if it cannot be determined.
     */
    public int port() {
        return ofNullable(fromExchange(httpExchange -> httpExchange.getRemoteAddress().getPort()))
            .or(() -> ofNullable(headers).map(header -> header.asString(HttpHeaders.HOST)).map(value -> NanoUtils.split(value, ":"))
                .filter(a -> a.length > 1)
                .map(a -> a[1])
                .map(s -> convertObj(s, Integer.class))
            ).orElse(-1);
    }

    /**
     * Retrieves the {@link InetAddress} from the {@link HttpExchange}.
     *
     * @return an {@link InetAddress} representing the remote address, or {@code null} if it is not available.
     */

    public InetAddress address() {
        return fromExchange(httpExchange -> httpExchange.getRemoteAddress().getAddress());
    }

    /**
     * Retrieves the protocol used in the {@link HttpExchange}.
     *
     * @return the protocol as a string, or {@code null} if the exchange is not available.
     */
    public String protocol() {
        return fromExchange(HttpExchange::getProtocol);
    }

    /**
     * Retrieves the character set encoding from the {@link HttpHeaders#CONTENT_TYPE} header.
     *
     * @return a {@link Charset} object representing the encoding specified in the {@link HttpHeaders#CONTENT_TYPE} header,
     * or the {@link Charset#defaultCharset()} if none is specified.
     */
    public Charset encoding() {
        return Arrays.stream(ofNullable(header(CONTENT_TYPE)).map(s -> NanoUtils.split(s, ";")).orElse(new String[0]))
            .map(String::trim)
            .filter(part -> part.toLowerCase().startsWith("charset="))
            .map(charset -> charset.substring(8).trim())
            .map(charset -> convertObj(charset, Charset.class))
            .filter(Objects::nonNull)
            .findFirst().orElse(Charset.defaultCharset());
    }

    public boolean hasContentType(final String... contentTypes) {
        final List<ContentType> result = contentTypes();
        return Arrays.stream(contentTypes).map(ContentType::fromValue).allMatch(result::contains);
    }

    public boolean hasContentType(final ContentType... contentTypes) {
        final List<ContentType> result = contentTypes();
        return Arrays.stream(contentTypes).allMatch(result::contains);
    }

    public boolean hasContentType(final ContentType contentType) {
        return contentTypes().contains(contentType);
    }

    public boolean isMethodGet() {
        return HttpMethod.GET.equals(method);
    }

    public boolean isMethodPost() {
        return HttpMethod.POST.equals(method);
    }

    public boolean isMethodPut() {
        return HttpMethod.PUT.equals(method);
    }

    public boolean isMethodHead() {
        return HttpMethod.HEAD.equals(method);
    }

    public boolean isMethodPatch() {
        return HttpMethod.PATCH.equals(method);
    }

    public boolean isMethodDelete() {
        return HttpMethod.DELETE.equals(method);
    }

    public boolean isMethodOptions() {
        return HttpMethod.OPTIONS.equals(method);
    }

    public boolean isMethodTrace() {
        return HttpMethod.TRACE.equals(method);
    }

    public boolean hasContentTypeJson() {
        return hasContentType(ContentType.APPLICATION_JSON);
    }

    public boolean hasContentTypeXml() {
        return hasContentType(ContentType.APPLICATION_XML);
    }

    public boolean hasContentTypeXmlSoap() {
        return hasContentType(ContentType.APPLICATION_SOAP_XML);
    }

    public boolean hasContentTypeOctetStream() {
        return hasContentType(ContentType.APPLICATION_OCTET_STREAM);
    }

    public boolean hasContentTypePdf() {
        return hasContentType(ContentType.APPLICATION_PDF);
    }

    public boolean hasContentTypeFormUrlEncoded() {
        return hasContentType(ContentType.APPLICATION_FORM_URLENCODED);
    }

    public boolean hasContentTypeMultiPartFormData() {
        return hasContentType(ContentType.MULTIPART_FORM_DATA);
    }

    public boolean hasContentTypePlainText() {
        return hasContentType(ContentType.TEXT_PLAIN);
    }

    public boolean hasContentTypeHtml() {
        return hasContentType(ContentType.TEXT_HTML);
    }

    public boolean hasContentTypeJpeg() {
        return hasContentType(ContentType.IMAGE_JPEG);
    }

    public boolean hasContentTypePng() {
        return hasContentType(ContentType.IMAGE_PNG);
    }

    public boolean hasContentTypeGif() {
        return hasContentType(ContentType.IMAGE_GIF);
    }

    public boolean hasContentTypeMpeg() {
        return hasContentType(ContentType.AUDIO_MPEG);
    }

    public boolean hasContentTypeMp4() {
        return hasContentType(ContentType.VIDEO_MP4);
    }

    public boolean hasAcceptJson() {
        return hasContentType(ContentType.APPLICATION_JSON);
    }

    public boolean hasAcceptXml() {
        return hasContentType(ContentType.APPLICATION_XML);
    }

    public boolean hasAcceptXmlSoap() {
        return hasContentType(ContentType.APPLICATION_SOAP_XML);
    }

    public boolean hasAcceptOctetStream() {
        return hasContentType(ContentType.APPLICATION_OCTET_STREAM);
    }

    public boolean hasAcceptPdf() {
        return hasContentType(ContentType.APPLICATION_PDF);
    }

    public boolean hasAcceptFormUrlEncoded() {
        return hasContentType(ContentType.APPLICATION_FORM_URLENCODED);
    }

    public boolean hasAcceptMultiPartFormData() {
        return hasContentType(ContentType.MULTIPART_FORM_DATA);
    }

    public boolean hasAcceptPlainText() {
        return hasContentType(ContentType.TEXT_PLAIN);
    }

    public boolean hasAcceptHtml() {
        return hasContentType(ContentType.TEXT_HTML);
    }

    public boolean hasAcceptJpeg() {
        return hasContentType(ContentType.IMAGE_JPEG);
    }

    public boolean hasAcceptPng() {
        return hasContentType(ContentType.IMAGE_PNG);
    }

    public boolean hasAcceptGif() {
        return hasContentType(ContentType.IMAGE_GIF);
    }

    public boolean hasAcceptMpeg() {
        return hasContentType(ContentType.AUDIO_MPEG);
    }

    public boolean hasAcceptMp4() {
        return hasContentType(ContentType.VIDEO_MP4);
    }

    public boolean is1xxInformational() {
        return statusCode >= 100 && statusCode < 200;
    }

    public boolean is2xxSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean is3xxRedirection() {
        return statusCode >= 300 && statusCode < 400;
    }

    public boolean is4xxClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean is5xxServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public <T> T fromExchange(final Function<HttpExchange, T> mapper) {
        return exchange != null ? mapper.apply(exchange) : null;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", HttpObject.class.getSimpleName() + "[", "]")
            .add("statusCode=" + statusCode)
            .add("path=" + path)
            .add("method=" + method())
            .add("headers=" + headers)
            .add("body=" + bodyAsString())
            .toString();
    }

    // ########## STATICS ##########

    public static ContentType guessContentType(final HttpObject response, final byte[] body) {
        if (body.length > 0) {
            if ((body[0] == '{' && body[body.length - 1] == '}') || (body[0] == '[' && body[body.length - 1] == ']')) {
                return ContentType.APPLICATION_JSON;
            } else if (body[0] == '<' && body[body.length - 1] == '>') {
                if (new String(body, 0, Math.min(body.length, 14), response.encoding()).contains("<!DOCTYPE html")) {
                    return ContentType.TEXT_HTML;
                } else {
                    return ContentType.APPLICATION_XML;
                }
            }
        }
        return ContentType.TEXT_PLAIN;
    }

    public static <R> List<R> splitHeaderValue(final Collection<String> value, final Function<String, R> mapper) {
        if (value == null)
            return emptyList();
        if (value.size() != 1)
            return value.stream().map(mapper).toList();
        return Arrays.stream(NanoUtils.split(value.iterator().next(), ","))
            .map(s -> NanoUtils.split(s, ";q="))
            .sorted(Comparator.comparing(parts -> parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 1.0, Comparator.reverseOrder()))
            .map(parts -> mapper.apply(parts[0].trim()))
            .filter(Objects::nonNull)
            .toList();
    }

    public static boolean isMethod(final HttpObject request, final HttpMethod method) {
        return request.method.name().equals(method.name());
    }

    /**
     * Converts a map of simple header values to a {@link TypeMap} of headers.
     *
     * @param headers a map containing header names and values.
     * @return a {@link TypeMap} representing the headers.
     */
    public static TypeMap convertHeaders(final Map<String, ?> headers) {
        return headers.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .collect(Collectors.toMap(
                entry -> entry.getKey().toLowerCase(),
                Map.Entry::getValue,
                (v1, v2) -> v1,
                TypeMap::new
            ));
    }

    public static String removeLast(final String input, final String removable) {
        return input.length() > removable.length() && input.endsWith(removable) ? input.substring(0, input.length() - removable.length()) : input;
    }

    public boolean isDeflateCompressed(final byte[] body) {
        final Inflater inflater = new Inflater();
        try {
            inflater.setInput(body);
            final byte[] result = new byte[100];
            final int resultLength = inflater.inflate(result);
            inflater.end();
            return resultLength > 0; // If decompression works, it's likely a DEFLATE compressed data
        } catch (final Exception e) {
            return false; // If an error occurs, it likely wasn't compressed data
        }
    }

    public boolean isZipCompressed(final byte[] body) {
        final int ZIP_MAGIC = 0x504B0304;
        return body.length > 3
            && (body[0] & 0xFF) == ((ZIP_MAGIC >> 24) & 0xFF)
            && (body[1] & 0xFF) == ((ZIP_MAGIC >> 16) & 0xFF)
            && (body[2] & 0xFF) == ((ZIP_MAGIC >> 8) & 0xFF)
            && (body[3] & 0xFF) == (ZIP_MAGIC & 0xFF);
    }
}
