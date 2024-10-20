package org.nanonative.nano.services.http.model;

/**
 * Common HTTP headers - used in requests and responses.
 * use always lowercase to avoid confusions and case sensitivity issues - makes lives easier.
 */
@SuppressWarnings("unused")
public class HttpHeaders {

    private HttpHeaders() {
    }

    public static final String ACCEPT = "accept";
    public static final String ACCEPT_CHARSET = "accept-charset";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String ACCEPT_LANGUAGE = "accept-language";
    public static final String ACCEPT_RANGES = "accept-ranges";
    public static final String AGE = "age";
    public static final String ALLOW = "allow";
    public static final String AUTHORIZATION = "authorization";
    public static final String CACHE_CONTROL = "cache-control";
    public static final String CONNECTION = "connection";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String CONTENT_LANGUAGE = "content-language";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_LOCATION = "content-location";
    public static final String CONTENT_MD5 = "content-md5";
    public static final String CONTENT_RANGE = "content-range";
    public static final String CONTENT_TYPE = "content-type";
    public static final String DATE = "date";
    public static final String DAV = "dav";
    public static final String DEPTH = "depth";
    public static final String DESTINATION = "destination";
    public static final String ETAG = "etag";
    public static final String EXPECT = "expect";
    public static final String EXPIRES = "expires";
    public static final String FROM = "from";
    public static final String HOST = "host";
    public static final String IF = "if";
    public static final String IF_MATCH = "if-match";
    public static final String IF_MODIFIED_SINCE = "if-modified-since";
    public static final String IF_NONE_MATCH = "if-none-match";
    public static final String IF_RANGE = "if-range";
    public static final String IF_UNMODIFIED_SINCE = "if-unmodified-since";
    public static final String LAST_MODIFIED = "last-modified";
    public static final String LOCATION = "location";
    public static final String LOCK_TOKEN = "lock-token";
    public static final String MAX_FORWARDS = "max-forwards";
    public static final String OVERWRITE = "overwrite";
    public static final String PRAGMA = "pragma";
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";
    public static final String RANGE = "range";
    public static final String REFERER = "referer";
    public static final String RETRY_AFTER = "retry-after";
    public static final String SERVER = "server";
    public static final String STATUS_URI = "status-uri";
    public static final String TE = "te";
    public static final String TIMEOUT = "timeout";
    public static final String TRAILER = "trailer";
    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String UPGRADE = "upgrade";
    public static final String USER_AGENT = "user-agent";
    public static final String VARY = "vary";
    public static final String VIA = "via";
    public static final String WARNING = "warning";
    public static final String WWW_AUTHENTICATE = "www-authenticate";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "access-control-allow-origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "access-control-allow-methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "access-control-allow-headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "access-control-max-age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "access-control-allow-credentials";
    public static final String ACCESS_CONTROL_REQUEST_METHOD = "access-control-request-method";
    public static final String ACCESS_CONTROL_REQUEST_HEADERS = "access-control-request-headers";
    public static final String ORIGIN = "origin";
}
