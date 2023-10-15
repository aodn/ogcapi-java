package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.tile.model.Link;
import org.springframework.http.MediaType;

@FunctionalInterface
public interface Converter<F, T> {
    T convert(F from);

    default Link getSelfLink(String hostname, String id) {
        Link self = new Link();

        self.rel("self");
        self.type(MediaType.APPLICATION_JSON_VALUE);
        self.href(String.format("%s/collections/%s/tiles/WebMercatorQuad",hostname, id));

        return self;
    }

    default Link getTileSchema(String hostname) {
        Link schema = new Link();
        schema.rel("http://www.opengis.net/def/rel/ogc/1.0/tiling-scheme");
        schema.type(MediaType.APPLICATION_JSON_VALUE);
        schema.href(String.format("%s/tileMatrixSets/WebMercatorQuad",hostname));

        return schema;
    }
}
