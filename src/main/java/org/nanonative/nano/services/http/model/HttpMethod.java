package org.nanonative.nano.services.http.model;

import java.util.Arrays;

@SuppressWarnings("unused")
public enum HttpMethod {
    GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;

    public static HttpMethod httpMethodOf(final String method) {
        return Arrays.stream(HttpMethod.values())
            .filter(httpMethod -> httpMethod.name().equalsIgnoreCase(method))
            .findFirst().orElse(null);
    }
}

