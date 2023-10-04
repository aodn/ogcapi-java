package au.org.aodn.ogcapi.server.core;

import org.springframework.http.MediaType;

public enum OGCMapper {
    html("html", MediaType.TEXT_HTML),
    json("json", MediaType.APPLICATION_JSON);

    protected MediaType mediaType;
    protected String ogcFormat;

    OGCMapper(String f, MediaType mediaType) {
        this.ogcFormat = f;
        this.mediaType = mediaType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
