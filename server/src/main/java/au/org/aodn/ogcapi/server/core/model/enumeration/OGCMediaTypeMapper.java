package au.org.aodn.ogcapi.server.core.model.enumeration;

import org.springframework.http.MediaType;

/**
 * OGC api have defined a couple of string for media type, we need a map so for Java code to work.
 */
public enum OGCMediaTypeMapper {
    html("html", MediaType.TEXT_HTML),
    json("json", MediaType.APPLICATION_JSON),
    geojson("application/geo+json", MediaType.parseMediaType("application/geo+json")),
    mapbox("application/vnd.mapbox-vector-tile", MediaType.parseMediaType("application/vnd.mapbox-vector-tile"));

    protected MediaType mediaType;
    protected String ogcFormat;

    OGCMediaTypeMapper(String f, MediaType mediaType) {
        this.ogcFormat = f;
        this.mediaType = mediaType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public static OGCMediaTypeMapper convert(String f) {
        return convert(f, OGCMediaTypeMapper.json);
    }

    public static OGCMediaTypeMapper convert(String f, OGCMediaTypeMapper defaultValue) {
        return f == null ? defaultValue : OGCMediaTypeMapper.valueOf(f.trim().toLowerCase());
    }
}
