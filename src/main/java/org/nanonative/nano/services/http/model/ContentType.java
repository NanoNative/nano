package org.nanonative.nano.services.http.model;

// TODO: autogenerate from org.apache.http.entity.ContentType
@SuppressWarnings("unused")
public enum ContentType {

    APPLICATION_ATOM_XML("application/atom+xml"),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded"),
    APPLICATION_JSON("application/json"),
    APPLICATION_PDF("application/pdf"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    APPLICATION_SOAP_XML("application/soap+xml"),
    APPLICATION_SVG_XML("application/svg+xml"),
    APPLICATION_XHTML_XML("application/xhtml+xml"),
    APPLICATION_XML("application/xml"),
    APPLICATION_PROBLEM_JSON("application/problem+json"),
    APPLICATION_PROBLEM_XML("application/problem+xml"),
    IMAGE_BMP("image/bmp"),
    IMAGE_GIF("image/gif"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_SVG("image/svg+xml"),
    IMAGE_TIFF("image/tiff"),
    IMAGE_WEBP("image/webp"),
    MULTIPART_FORM_DATA("multipart/form-data"),
    TEXT_HTML("text/html"),
    TEXT_PLAIN("text/plain"),
    TEXT_XML("text/xml"),
    WILDCARD("*/*"),
    AUDIO_MPEG("audio/mpeg"),
    VIDEO_MP4("video/mp4");

    private final String value;

    ContentType(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ContentType fromValue(final String value) {
        for (final ContentType type : values()) {
            if (type.value().equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
