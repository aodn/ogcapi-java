package au.org.aodn.ogcapi.server.core;

import org.springframework.http.MediaType;

/**
 * OGC api have defined a couple of string for media type, we need a map so for Java code to work.
 */
public enum OGCMediaTypeMapper {
    html("html", MediaType.TEXT_HTML),
    json("json", MediaType.APPLICATION_JSON);

    protected MediaType mediaType;
    protected String ogcFormat;

    OGCMediaTypeMapper(String f, MediaType mediaType) {
        this.ogcFormat = f;
        this.mediaType = mediaType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
