package org.nanonative.nano.model;

import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMap;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpHeaders;
import org.nanonative.nano.services.http.model.HttpMethod;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.nano.helper.event.model.Event.eventOf;
import static org.nanonative.nano.services.http.HttpService.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.http.model.ContentType.APPLICATION_JSON;
import static org.nanonative.nano.services.http.model.ContentType.APPLICATION_PDF;
import static org.nanonative.nano.services.http.model.ContentType.TEXT_PLAIN;
import static org.nanonative.nano.services.http.model.ContentType.WILDCARD;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT_ENCODING;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCEPT_LANGUAGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.nanonative.nano.services.http.model.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.AUTHORIZATION;
import static org.nanonative.nano.services.http.model.HttpHeaders.CACHE_CONTROL;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_LENGTH;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_RANGE;
import static org.nanonative.nano.services.http.model.HttpHeaders.CONTENT_TYPE;
import static org.nanonative.nano.services.http.model.HttpHeaders.HOST;
import static org.nanonative.nano.services.http.model.HttpHeaders.REFERER;
import static org.nanonative.nano.services.http.model.HttpHeaders.USER_AGENT;
import static org.nanonative.nano.services.http.model.HttpHeaders.VARY;

class HttpObjectTest {

    @Test
    void browserRequestTest() {
        final HttpObject httpObject = new HttpObject()
            .methodType(HttpMethod.GET)
            .path("/notifications/indicator")
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Accept-Language", "en-GB,en;q=0.9")
            .header("Connection", "keep-alive")
            .header("Host", "example.com")
            .header("Referer", "https://example.com/test")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15")
            .header("X-Requested-With", "XMLHttpRequest");
        assertThat(httpObject.methodType()).isEqualTo(HttpMethod.GET);
        assertThat(httpObject.path()).isEqualTo("/notifications/indicator");
        assertThat(httpObject.accepts()).containsExactly(APPLICATION_JSON);
        assertThat(httpObject.acceptEncodings()).containsExactly("gzip", "deflate");
        assertThat(httpObject.acceptLanguages()).containsExactly(Locale.UK, ENGLISH);
        assertThat(httpObject.header(HOST)).isEqualTo("example.com");
        assertThat(httpObject.header(REFERER)).isEqualTo("https://example.com/test");
        assertThat(httpObject.userAgent()).isEqualTo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15");
        assertThat(httpObject.header("X-Requested-With")).isEqualTo("XMLHttpRequest");
    }

    @Test
    void testRespondResponse() {
        final Event event = eventOf(Context.createRootContext(HttpObjectTest.class), EVENT_HTTP_REQUEST).payload(() -> new HttpObject().methodType(HttpMethod.GET).path("/create"));

        event.payloadOpt(HttpObject.class)
            .filter(HttpObject::isMethodGet)
            .filter(request -> request.pathMatch("/create"))
            .ifPresent(request -> request.response().statusCode(201).body("success").respond(event));

        assertThat(event.responseOpt(HttpObject.class)).isPresent();
        assertThat(event.response(HttpObject.class).statusCode()).isEqualTo(201);
        assertThat(event.response(HttpObject.class).bodyAsString()).isEqualTo("success");
    }

    @Test
    void testCorsResponse() {
        final HttpObject request = new HttpObject().header("origin", "aa.bb.cc");

        // DEFAULT
        assertThat(new HttpObject().response(true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(new HttpObject().corsResponse().headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(new HttpObject().header("host", "aa.bb.cc").response(true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.response(true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        // CUSTOM
        assertThat(request.corsResponse("*").headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc").headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "GET",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF").headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Accept, Authorization, X-Requested-With",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF", "GG, HH, II").headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF", "GG, HH, II", -99).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "86400",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF", "GG, HH, II", 99).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF", "GG, HH, II", 99, true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "true",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("aa.bb.cc", "DD, EE, FF", "GG, HH, II", 99, false).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("*", "DD, EE, FF", "GG, HH, II", 99, true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "true",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("*", "DD, EE, FF", "GG, HH, II", 99, false).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("11.22.33, aa.bb.cc", "DD, EE, FF", "GG, HH, II", 99, true).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "true",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));

        assertThat(request.corsResponse("11.22.33, aa.bb.cc", "DD, EE, FF", "GG, HH, II", 99, false).headerMap()).containsAllEntriesOf(Map.of(
            ACCESS_CONTROL_ALLOW_CREDENTIALS, "false",
            ACCESS_CONTROL_ALLOW_HEADERS, "GG, HH, II",
            ACCESS_CONTROL_ALLOW_METHODS, "DD, EE, FF",
            ACCESS_CONTROL_ALLOW_ORIGIN, "aa.bb.cc",
            ACCESS_CONTROL_MAX_AGE, "99",
            VARY, "Origin"
        ));
    }

    @Test
    void testStatusCodeFamilies() {
        final HttpObject httpObject = new HttpObject();

        for (int statusCode = 100; statusCode < 600; statusCode++) {
            httpObject.statusCode(statusCode);

            // Check the correct status family
            final boolean is1xx = statusCode >= 100 && statusCode < 200;
            final boolean is2xx = statusCode >= 200 && statusCode < 300;
            final boolean is3xx = statusCode >= 300 && statusCode < 400;
            final boolean is4xx = statusCode >= 400 && statusCode < 500;
            final boolean is5xx = statusCode >= 500 && statusCode < 600;

            assertThat(httpObject.is1xxInformational()).as("Check 1xx for status %s", statusCode).isEqualTo(is1xx);
            assertThat(httpObject.is2xxSuccessful()).as("Check 2xx for status %s", statusCode).isEqualTo(is2xx);
            assertThat(httpObject.is3xxRedirection()).as("Check 3xx for status %s", statusCode).isEqualTo(is3xx);
            assertThat(httpObject.is4xxClientError()).as("Check 4xx for status %s", statusCode).isEqualTo(is4xx);
            assertThat(httpObject.is5xxServerError()).as("Check 5xx for status %s", statusCode).isEqualTo(is5xx);
        }
    }

    @Test
    void testConstructor_withHttpExchange() {
        final Headers headers = new Headers();
        headers.add(CONTENT_TYPE, APPLICATION_JSON.value());
        final HttpObject httpObject = new HttpObject(createMockHttpExchange("GET", "/test", headers, "{\"key\": \"value\"}"));

        assertThat(httpObject.methodType()).isEqualTo(HttpMethod.GET);
        assertThat(httpObject.path()).isEqualTo("/test");
        assertThat(httpObject.headerMap()).containsEntry(CONTENT_TYPE, Collections.singletonList(APPLICATION_JSON.value()));
        assertThat(httpObject.exchange()).isNotNull();
    }

    @Test
    void testBuilder() {
        final HttpObject httpObject = new HttpObject()
            .methodType(HttpMethod.GET)
            .path("/test")
            .statusCode(-99)
            .contentType(APPLICATION_JSON);

        assertThat(httpObject.methodType()).isEqualTo(HttpMethod.GET);
        assertThat(httpObject.path()).isEqualTo("/test");
        assertThat(httpObject.headerMap()).containsEntry(CONTENT_TYPE, APPLICATION_JSON.value());
        assertThat(httpObject.statusCode()).isEqualTo(-99);
        assertThat(httpObject.exchange()).isNull();
    }

    @Test
    void testConvertHeaders() {
        final Headers headers = new Headers();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "application/json");
        final TypeMap typeMap = HttpObject.convertHeaders(headers);
        assertThat(typeMap)
            .containsEntry("content-type", Collections.singletonList("application/json"))
            .containsEntry("accept", Collections.singletonList("application/json"));
    }

    @Test
    void testIsMethod() {
        for (final HttpMethod testMethod : HttpMethod.values()) {
            final HttpObject httpObject = new HttpObject().methodType(testMethod);
            for (final HttpMethod otherMethod : HttpMethod.values()) {
                assertThat(HttpObject.isMethod(httpObject, otherMethod)).isEqualTo(otherMethod == testMethod);
            }
        }
    }

    @Test
    void testIsMethodGet() {
        final HttpObject httpObject = new HttpObject().methodType(HttpMethod.GET);
        assertThat(httpObject.isMethodGet()).isTrue();
        assertThat(httpObject.isMethodPost()).isFalse();
        assertThat(httpObject.isMethodPut()).isFalse();
        assertThat(httpObject.isMethodHead()).isFalse();
        assertThat(httpObject.isMethodPatch()).isFalse();
        assertThat(httpObject.isMethodDelete()).isFalse();
        assertThat(httpObject.isMethodOptions()).isFalse();
        assertThat(httpObject.isMethodTrace()).isFalse();
    }

    @Test
    void testSetMethod() {
        assertThat(new HttpObject().methodType(HttpMethod.GET).methodType()).isEqualTo(HttpMethod.GET);
        assertThat(new HttpObject().methodType("PUT").methodType()).isEqualTo(HttpMethod.PUT);
        assertThat(new HttpObject().methodType("pAtCh").methodType()).isEqualTo(HttpMethod.PATCH);
        assertThat(new HttpObject().methodType("unknown").methodType()).isNull();
    }

    @Test
    void testHeaderContentType() {
        // ENUM
        assertThat(new HttpObject().contentType(APPLICATION_JSON).contentType()).isEqualTo(APPLICATION_JSON);
        assertThat(new HttpObject().contentType(APPLICATION_JSON).contentTypes()).containsExactly(APPLICATION_JSON);
        assertThat(new HttpObject().contentType(APPLICATION_JSON, TEXT_PLAIN).contentTypes()).containsExactly(APPLICATION_JSON, TEXT_PLAIN);
        assertThat(new HttpObject().contentType(APPLICATION_JSON, TEXT_PLAIN).header(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON.value() + ", " + TEXT_PLAIN.value());
        assertThat(new HttpObject().contentType(APPLICATION_JSON, TEXT_PLAIN).hasContentType(APPLICATION_JSON)).isTrue();
        assertThat(new HttpObject().contentType(APPLICATION_JSON, TEXT_PLAIN).hasContentType(APPLICATION_JSON, TEXT_PLAIN)).isTrue();
        assertThat(new HttpObject().contentType(APPLICATION_JSON, TEXT_PLAIN).hasContentType(APPLICATION_JSON, TEXT_PLAIN, APPLICATION_PDF)).isFalse();

        // STRING
        assertThat(new HttpObject().contentType("application/json").contentType()).isEqualTo(APPLICATION_JSON);
        assertThat(new HttpObject().contentType("application/json").contentTypes()).containsExactly(APPLICATION_JSON);
        assertThat(new HttpObject().contentType("application/json", "text/plain").contentTypes()).containsExactly(APPLICATION_JSON, TEXT_PLAIN);
        assertThat(new HttpObject().contentType("application/json", "TexT/Plain").header(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON.value() + ", " + TEXT_PLAIN.value());
        assertThat(new HttpObject().contentType("application/json", "TexT/Plain").hasContentType(APPLICATION_JSON.value())).isTrue();
        assertThat(new HttpObject().contentType("application/json", "TexT/Plain").hasContentType(APPLICATION_JSON.value(), TEXT_PLAIN.value())).isTrue();
        assertThat(new HttpObject().contentType("application/json", "TexT/Plain").hasContentType(APPLICATION_JSON.value(), TEXT_PLAIN.value(), APPLICATION_PDF.value())).isFalse();

        // General
        assertThat(new HttpObject().contentTypes()).containsExactly(TEXT_PLAIN);
        assertThat(new HttpObject().contentType()).isEqualTo(TEXT_PLAIN);
    }

    @Test
    void testHasContentTypeMethods() {
        final HttpObject httpObject = new HttpObject().contentType(APPLICATION_JSON);
        assertThat(httpObject.hasContentType("application/json")).isTrue();
        assertThat(httpObject.hasContentTypeJson()).isTrue();
        assertThat(httpObject.hasAcceptJson()).isTrue();
        assertThat(httpObject.hasContentTypeHtml()).isFalse();
        assertThat(httpObject.hasContentTypeMp4()).isFalse();
        assertThat(httpObject.hasContentTypeGif()).isFalse();
        assertThat(httpObject.hasContentTypeJpeg()).isFalse();
        assertThat(httpObject.hasContentTypeMpeg()).isFalse();
        assertThat(httpObject.hasContentTypePdf()).isFalse();
        assertThat(httpObject.hasContentTypePng()).isFalse();
        assertThat(httpObject.hasContentTypeXml()).isFalse();
        assertThat(httpObject.hasContentTypeOctetStream()).isFalse();
        assertThat(httpObject.hasContentTypeMultiPartFormData()).isFalse();
        assertThat(httpObject.hasContentTypePlainText()).isFalse();
        assertThat(httpObject.hasContentTypeXmlSoap()).isFalse();
        assertThat(httpObject.hasContentTypeFormUrlEncoded()).isFalse();
        assertThat(httpObject.hasAcceptXml()).isFalse();
        assertThat(httpObject.hasAcceptXmlSoap()).isFalse();
        assertThat(httpObject.hasAcceptOctetStream()).isFalse();
        assertThat(httpObject.hasAcceptPdf()).isFalse();
        assertThat(httpObject.hasAcceptFormUrlEncoded()).isFalse();
        assertThat(httpObject.hasAcceptMultiPartFormData()).isFalse();
        assertThat(httpObject.hasAcceptPlainText()).isFalse();
        assertThat(httpObject.hasAcceptHtml()).isFalse();
        assertThat(httpObject.hasAcceptJpeg()).isFalse();
        assertThat(httpObject.hasAcceptPng()).isFalse();
        assertThat(httpObject.hasAcceptGif()).isFalse();
        assertThat(httpObject.hasAcceptMpeg()).isFalse();
        assertThat(httpObject.hasAcceptMp4()).isFalse();
    }

    @Test
    void testContentTypeEncoding() {
        // ENUM
        assertThat(new HttpObject().contentType(APPLICATION_JSON).encoding()).isEqualTo(Charset.defaultCharset());
        assertThat(new HttpObject().contentType(US_ASCII, APPLICATION_PDF).header(CONTENT_TYPE)).isEqualTo(APPLICATION_PDF.value() + "; charset=" + US_ASCII);
        assertThat(new HttpObject().contentType(US_ASCII, APPLICATION_PDF).encoding()).isEqualTo(US_ASCII);

        // STRING
        assertThat(new HttpObject().contentType(APPLICATION_JSON.value()).encoding()).isEqualTo(Charset.defaultCharset());
        assertThat(new HttpObject().contentType(US_ASCII, APPLICATION_PDF.value()).header(CONTENT_TYPE)).isEqualTo(APPLICATION_PDF.value() + "; charset=" + US_ASCII);
        assertThat(new HttpObject().contentType(US_ASCII, APPLICATION_PDF.value()).encoding()).isEqualTo(US_ASCII);
    }

    @Test
    void testHeaderAccept() {
        // ENUM
        assertThat(new HttpObject().accept(APPLICATION_JSON).accept()).isEqualTo(APPLICATION_JSON);
        assertThat(new HttpObject().accept(APPLICATION_JSON).accepts()).containsExactly(APPLICATION_JSON);
        assertThat(new HttpObject().accept(APPLICATION_JSON, TEXT_PLAIN).accepts()).containsExactly(APPLICATION_JSON, TEXT_PLAIN);
        assertThat(new HttpObject().accept(APPLICATION_JSON, TEXT_PLAIN).header(ACCEPT)).isEqualTo(APPLICATION_JSON.value() + ", " + TEXT_PLAIN.value());
        assertThat(new HttpObject().accept(APPLICATION_JSON, TEXT_PLAIN).hasAccept(APPLICATION_JSON)).isTrue();
        assertThat(new HttpObject().accept(APPLICATION_JSON, TEXT_PLAIN).hasAccept(APPLICATION_JSON, TEXT_PLAIN)).isTrue();
        assertThat(new HttpObject().accept(APPLICATION_JSON, TEXT_PLAIN).hasAccept(APPLICATION_JSON, TEXT_PLAIN, APPLICATION_PDF)).isFalse();

        // STRING
        assertThat(new HttpObject().accept("application/json").accept()).isEqualTo(APPLICATION_JSON);
        assertThat(new HttpObject().accept("application/json").accepts()).containsExactly(APPLICATION_JSON);
        assertThat(new HttpObject().accept("application/json", "text/plain").accepts()).containsExactly(APPLICATION_JSON, TEXT_PLAIN);
        assertThat(new HttpObject().accept("application/json", "TexT/Plain").header(ACCEPT)).isEqualTo(APPLICATION_JSON.value() + ", " + TEXT_PLAIN.value());
        assertThat(new HttpObject().accept("application/json", "text/plain").hasAccept(APPLICATION_JSON.value())).isTrue();
        assertThat(new HttpObject().accept("application/json", "text/plain").hasAccept(APPLICATION_JSON.value(), TEXT_PLAIN.value())).isTrue();
        assertThat(new HttpObject().accept("application/json", "text/plain").hasAccept(APPLICATION_JSON.value(), TEXT_PLAIN.value(), APPLICATION_PDF.value())).isFalse();

        // General
        assertThat(new HttpObject().accepts()).isEmpty();
        assertThat(new HttpObject().accept()).isNull();
    }

    @Test
    void testBody() {
        final String bodyString = "{\"key\":\"value\"}";
        final TypeMap bodyJson = new TypeMap().putR("key", "value");
        final byte[] bodyBytes = bodyString.getBytes(Charset.defaultCharset());
        //TODO: test bodyAsXml

        // null body
        final HttpObject nullTest = new HttpObject();
        assertThat(nullTest.body()).isEqualTo(new byte[0]);
        assertThat(nullTest.bodyAsString()).isEmpty();
        assertThat(nullTest.bodyAsJson()).isEqualTo(new TypeList().addR(""));
        assertThat(nullTest.bodyAsXml()).isNotNull();
        assertThat(nullTest.bodyAsJson().asString("key")).isNull();

        // Byte[] body
        final HttpObject byteTest = new HttpObject().body(bodyString.getBytes(Charset.defaultCharset()));
        assertThat(byteTest.body()).isEqualTo(bodyBytes);
        assertThat(byteTest.bodyAsString()).isEqualTo(bodyString);
        assertThat(byteTest.bodyAsJson()).isEqualTo(bodyJson);
        assertThat(byteTest.bodyAsXml()).isNotNull();
        assertThat(byteTest.bodyAsJson().asString("key")).isEqualTo("value");

        // String body
        final HttpObject stringTest = new HttpObject().body(bodyString);
        assertThat(stringTest.body()).isEqualTo(bodyBytes);
        assertThat(stringTest.bodyAsString()).isEqualTo(bodyString);
        assertThat(stringTest.bodyAsJson()).isEqualTo(bodyJson);
        assertThat(stringTest.bodyAsXml()).isNotNull();
        assertThat(stringTest.bodyAsJson().asString("key")).isEqualTo("value");

        // JSON body
        final HttpObject jsonTest = new HttpObject().body(bodyJson);
        assertThat(jsonTest.body()).isEqualTo(bodyBytes);
        assertThat(jsonTest.bodyAsString()).isEqualTo(bodyString);
        assertThat(jsonTest.bodyAsJson()).isEqualTo(bodyJson);
        assertThat(jsonTest.bodyAsXml()).isNotNull();
        assertThat(jsonTest.bodyAsJson().asString("key")).isEqualTo("value");

        // HttpExchange body
        final HttpObject exchangeTest = new HttpObject(createMockHttpExchange("GET", "/test", new Headers(), bodyString));
        assertThat(exchangeTest.body()).isEqualTo(bodyBytes);
        assertThat(exchangeTest.bodyAsString()).isEqualTo(bodyString);
        assertThat(exchangeTest.bodyAsJson()).isEqualTo(bodyJson);
        assertThat(exchangeTest.bodyAsXml()).isNotNull();
        assertThat(exchangeTest.bodyAsJson().asString("key")).isEqualTo("value");

        // General
        assertThat(new HttpObject().bodyAsJson()).isEqualTo(new TypeList().addR(""));
        assertThat(new HttpObject().bodyAsXml()).isEqualTo(new TypeList());
        assertThat(new HttpObject().bodyAsString()).isEmpty();
    }

    @Test
    void testQueryParameters() {
        final HttpObject httpObject = new HttpObject().path("/test?key1=value1&key2=value2");
        assertThat(httpObject.queryParams()).hasSize(2);
        assertThat(httpObject.containsQueryParam("key1")).isTrue();
        assertThat(httpObject.containsQueryParam("key2")).isTrue();
        assertThat(httpObject.containsQueryParam("key3")).isFalse();
        assertThat(httpObject.queryParam("key1")).isEqualTo("value1");
        assertThat(httpObject.queryParam("key2")).isEqualTo("value2");
        assertThat(httpObject.queryParam("key3")).isNull();

        // General
        assertThat(new HttpObject(createMockHttpExchange("GET", "/test?key1=value1&key2=value2", new Headers(), "{\"key\": \"value\"}")).queryParams()).hasSize(2);
        assertThat(new HttpObject().queryParams()).isEmpty();
    }

    @Test
    void testPathMatcher() {
        // no ending /
        final HttpObject httpObject1 = new HttpObject().path("/aa/bb/cc/dd?myNumber=2468");
        assertThat(httpObject1.pathMatch("/aa/*/cc/*")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/*/**")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/**")).isTrue();
        assertThat(httpObject1.pathMatch("/**")).isTrue();
        assertThat(httpObject1.pathMatch("/*")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/bb/cc/dd")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/bb/cc/dd/")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/{value1}/cc/{value2}/ee")).isFalse();
        assertThat(httpObject1.pathMatch("/aa/{value1}/cc/ee")).isFalse();
        assertThat(httpObject1.pathMatch("/aa/{value1}/cc/d")).isFalse();
        assertThat(httpObject1.pathMatch("/aa/{value1}/cc/{value2}")).isTrue();
        assertThat(httpObject1.pathMatch("/aa/{value1}/cc/{value2}/")).isTrue();
        assertThat(httpObject1.pathParam("value1")).isEqualTo("bb");
        assertThat(httpObject1.pathParams().asString("value2")).isEqualTo("dd");
        assertThat(httpObject1.queryParams().asInt("myNumber")).isEqualTo(2468);

        // with ending /
        final HttpObject httpObject2 = new HttpObject().path("/aa/bb/cc/dd/?myNumber=2468");
        assertThat(httpObject2.pathMatch("/aa/*/cc/*/")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/*/**/")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/**/")).isTrue();
        assertThat(httpObject2.pathMatch("/**/")).isTrue();
        assertThat(httpObject2.pathMatch("/*/")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/bb/cc/dd")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/bb/cc/dd/")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/{value1}/cc/{value2}/ee")).isFalse();
        assertThat(httpObject2.pathMatch("/aa/{value1}/cc/ee")).isFalse();
        assertThat(httpObject2.pathMatch("/aa/{value1}/cc/d")).isFalse();
        assertThat(httpObject2.pathMatch("/aa/{value1}/cc/{value2}")).isTrue();
        assertThat(httpObject2.pathMatch("/aa/{value1}/cc/{value2}/")).isTrue();
        assertThat(httpObject2.pathParam("value1")).isEqualTo("bb");
        assertThat(httpObject2.pathParams().asString("value2")).isEqualTo("dd");
        assertThat(httpObject2.queryParams().asInt("myNumber")).isEqualTo(2468);

        // General
        assertThat(new HttpObject().path(null).pathMatch("/aa/bb/cc/dd")).isFalse();
        assertThat(new HttpObject().path(null).pathMatch(null)).isFalse();
    }

    @Test
    void testHeaders() {
        // set headers
        final Headers headers = new Headers();
        headers.add("Content-Type", "Application/Json, TexT/Plain");
        headers.put("Accept", List.of(APPLICATION_PDF.value(), APPLICATION_JSON.value()));
        headers.add("myNumber", "123");
        final HttpObject httpObject1 = new HttpObject().headerMap(headers);

        // set headers map
        final HttpObject httpObject2 = new HttpObject().headerMap(Map.of(
            "Content-Type", "Application/Json, TexT/Plain",
            "Accept", List.of(APPLICATION_PDF, APPLICATION_JSON.value()),
            "myNumber", "123"
        ));

        // add headers
        final HttpObject httpObject3 = new HttpObject()
            .header("Content-Type", "Application/Json, TexT/Plain")
            .header("Accept", List.of(APPLICATION_PDF, APPLICATION_JSON.value()))
            .header("myNumber", "123")
            .header(null, "aa")
            .header("bb", null);

        for (final HttpObject httpObject : List.of(httpObject1, httpObject2, httpObject3)) {
            assertThat(httpObject.headerMap()).hasSize(3);
            assertThat(httpObject.containsHeader("mynumber")).isTrue();
            assertThat(httpObject.containsHeader("myNumber")).isTrue();
            assertThat(httpObject.containsHeader("invalid")).isFalse();
            assertThat(httpObject.containsHeader(null)).isFalse();
            assertThat(httpObject.header("myNumber")).isEqualTo("123");
            assertThat(httpObject.header("mynumber")).isEqualTo("123");
            assertThat(httpObject.header("invalid")).isNull();
            assertThat(httpObject.header(null)).isNull();
            assertThat(httpObject.headerMap().asInt("mynumber")).isEqualTo(123);
            assertThat(httpObject.contentTypes()).containsExactly(APPLICATION_JSON, TEXT_PLAIN);
            assertThat(httpObject.accepts()).containsExactly(APPLICATION_PDF, APPLICATION_JSON);
        }

        assertThat(new HttpObject().headerMap()).isEmpty();
    }

    @Test
    void testComputeHeaders() {
        final Map<String, List<String>> request = new HttpObject().computedHeaders(true);
        assertThat(request)
            .containsEntry(ACCEPT_ENCODING, List.of("gzip, deflate"))
            .containsEntry(ACCEPT, List.of(WILDCARD.value()))
            .containsEntry(CACHE_CONTROL, List.of("no-cache"))
            .containsEntry(CONTENT_TYPE, List.of(TEXT_PLAIN.value()))
            .containsEntry(CONTENT_LENGTH, List.of("0"))
        ;

        final Map<String, List<String>> response = new HttpObject().computedHeaders(false);
        assertThat(response)
            .doesNotContainKey(ACCEPT_ENCODING)
            .doesNotContainKey(ACCEPT)
            .containsEntry(CACHE_CONTROL, List.of("no-cache"))
            .containsEntry(CONTENT_TYPE, List.of(TEXT_PLAIN.value()))
            .containsEntry(CONTENT_LENGTH, List.of("0"))
        ;
    }

    @Test
    void sizeRequestTest() {
        final String body = "Hello World";
        assertThat(new HttpObject().sizeRequest()).isFalse();
        assertThat(new HttpObject().sizeRequest(true).sizeRequest()).isTrue();
        assertThat(new HttpObject().sizeRequest(false).sizeRequest()).isFalse();
        assertThat(new HttpObject().size()).isZero();
        assertThat(new HttpObject().body(body).size()).isEqualTo(body.getBytes().length);
        assertThat(new HttpObject().body(body).header(CONTENT_LENGTH, 999).size()).isEqualTo(11);
        assertThat(new HttpObject().body(body).header(CONTENT_LENGTH, 999).header(CONTENT_LENGTH)).isEqualTo("999");
        assertThat(new HttpObject().body(body).header(CONTENT_RANGE, "bytes 0-0/666").size()).isEqualTo(666);
    }

    @Test
    void testCaller() {
        final HttpObject httpObject1 = new HttpObject().header(HttpHeaders.HOST, "example.com:1337");
        assertThat(httpObject1.host()).isEqualTo("example.com");
        assertThat(httpObject1.address()).isNull();
        assertThat(httpObject1.port()).isEqualTo(1337);

        // with exchange
        final HttpObject httpObject2 = new HttpObject(createMockHttpExchange("GET", "/test", new Headers(), ""));
        assertThat(httpObject2.host()).isEqualTo("example.com");
        assertThat(httpObject2.address()).isNotNull();
        assertThat(httpObject2.port()).isEqualTo(1337);

        assertThat(new HttpObject().header(HttpHeaders.HOST, "no-port.com").port()).isEqualTo(-1);
    }

    @Test
    void testProtocol() {
        final HttpObject httpObject1 = new HttpObject().header(HttpHeaders.HOST, "example.com:1337");
        assertThat(httpObject1.protocol()).isNull();

        // with exchange
        final HttpObject httpObject2 = new HttpObject(createMockHttpExchange("GET", "/test", new Headers(), ""));
        assertThat(httpObject2.protocol()).isEqualTo("HTTP/1.1");
    }

    @Test
    void testHeaderUserAgent() {
        assertThat(new HttpObject().header(USER_AGENT, "PostmanRuntime/7.36.3").header(USER_AGENT)).isEqualTo("PostmanRuntime/7.36.3");
        assertThat(new HttpObject().header(USER_AGENT, "PostmanRuntime/7.36.3").userAgent()).isEqualTo("PostmanRuntime/7.36.3");

        final String macOsBrowser = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15";
        final String chromeMobile = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
        assertThat(new HttpObject().userAgent(macOsBrowser).isFrontendCall()).isTrue();
        assertThat(new HttpObject().userAgent(macOsBrowser).isMobileCall()).isFalse();
        assertThat(new HttpObject().userAgent(chromeMobile).isFrontendCall()).isTrue();
        assertThat(new HttpObject().userAgent(chromeMobile).isMobileCall()).isTrue();
        assertThat(new HttpObject().isFrontendCall()).isFalse();
        assertThat(new HttpObject().isMobileCall()).isFalse();
    }

    @Test
    void testAuthToken() {
        assertThat(new HttpObject().header(AUTHORIZATION, "Bearer 123").authTokens()).containsExactly("123");
        assertThat(new HttpObject().header(AUTHORIZATION, "123ABC").authTokens()).containsExactly("123ABC");
        assertThat(new HttpObject().header(AUTHORIZATION, "Basic QWxhZGRpbjpPcGVuU2VzYW1l").authTokens()).containsExactly("Aladdin", "OpenSesame");
        assertThat(new HttpObject().header(AUTHORIZATION, "Basic QWxhZGRpbjpPcGVuU2VzYW1l").authToken()).isEqualTo("Aladdin");
        assertThat(new HttpObject().header(AUTHORIZATION, "Basic QWxhZGRpbjpPcGVuU2VzYW1l").authToken(1)).isEqualTo("OpenSesame");
    }

    @Test
    void testHeaderLanguage() {
        final HttpObject httpObject = new HttpObject().header(ACCEPT_LANGUAGE, "en-US,en;q=0.9,de;q=0.8");
        assertThat(httpObject.acceptLanguage()).isEqualTo(Locale.of("en", "us"));
        assertThat(httpObject.acceptLanguages()).containsExactly(Locale.of("en", "us"), ENGLISH, Locale.GERMAN);

        // General
        assertThat(new HttpObject().acceptLanguages()).containsExactly(ENGLISH);
        assertThat(new HttpObject().acceptLanguage()).isEqualTo(ENGLISH);
        assertThat(new HttpObject().acceptLanguages(Locale.UK, ENGLISH, Locale.GERMAN).acceptLanguages()).containsExactly(Locale.UK, ENGLISH, Locale.GERMAN);
        assertThat(new HttpObject().acceptLanguages((Locale[]) null).acceptLanguages()).containsExactly(ENGLISH);
        assertThat(new HttpObject().acceptLanguages(new Locale[0]).acceptLanguages()).containsExactly(ENGLISH);
    }

    @Test
    void testHeaderAcceptEncoding() {
        final HttpObject httpObject = new HttpObject().header(ACCEPT_ENCODING, List.of("gzip", "deflate"));
        assertThat(httpObject.acceptEncoding()).isEqualTo("gzip");
        assertThat(httpObject.acceptEncodings()).containsExactly("gzip", "deflate");
        assertThat(httpObject.hasAcceptEncoding("gzip", "deflate")).isTrue();
        assertThat(httpObject.hasAcceptEncoding("gzip", "deflate", "unknown")).isFalse();

        // General
        assertThat(new HttpObject().acceptEncodings()).isEmpty();
        assertThat(new HttpObject().acceptEncoding()).isNull();
    }

    @Test
    void testHashCode() {
        final HttpObject httpObject1 = new HttpObject();
        httpObject1.statusCode(200)
            .body("Sample body".getBytes());

        final HttpObject httpObject2 = new HttpObject();
        httpObject2.statusCode(200)
            .body("Sample body".getBytes());

        assertThat(httpObject1.hashCode()).doesNotHaveSameHashCodeAs(httpObject2.hashCode());
        assertThat(httpObject1.equals(httpObject2)).isFalse();
        assertThat(httpObject1.equals(new HttpObject())).isFalse();
        assertThat(httpObject1.equals("invalid")).isFalse();
    }

    @Test
    void testToString() {
        final HttpObject httpObject = new HttpObject();
        httpObject
            .statusCode(200)
            .methodType(HttpMethod.GET)
            .path("/test")
            .body("Sample body");

        assertThat(httpObject).hasToString("HttpObject[statusCode=200, path=/test, method=GET, headers=null, body=Sample body]");
    }


    private HttpExchange createMockHttpExchange(final String method, final String path, final Headers headers, final String testBody) {

        return new HttpExchange() {
            @Override
            public Headers getRequestHeaders() {
                return headers;
            }

            @Override
            public Headers getResponseHeaders() {
                return null;
            }

            @Override
            public URI getRequestURI() {
                return URI.create("http://localhost" + path);
            }

            @Override
            public String getRequestMethod() {
                return method;
            }

            @Override
            public HttpContext getHttpContext() {
                return null;
            }

            @Override
            public void close() {

            }

            @Override
            public InputStream getRequestBody() {
                return new ByteArrayInputStream(testBody.getBytes(Charset.defaultCharset()));
            }

            @Override
            public OutputStream getResponseBody() {
                return null;
            }

            @Override
            public void sendResponseHeaders(final int rCode, final long responseLength) {

            }

            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("example.com", 1337);
            }

            @Override
            public int getResponseCode() {
                return 0;
            }

            @Override
            public InetSocketAddress getLocalAddress() {
                return null;
            }

            @Override
            public String getProtocol() {
                return "HTTP/1.1";
            }

            @Override
            public Object getAttribute(final String name) {
                return null;
            }

            @Override
            public void setAttribute(final String name, final Object value) {

            }

            @Override
            public void setStreams(final InputStream i, final OutputStream o) {

            }

            @Override
            public HttpPrincipal getPrincipal() {
                return null;
            }
        };
    }
}
