package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.*;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.stream.Collectors;

@FunctionalInterface
public interface Converter<F, T> {

    Logger logger = LoggerFactory.getLogger(Converter.class);

    T convert(F from);

    default au.org.aodn.ogcapi.features.model.Link getSelfCollectionLink(String hostname, String id) {
        au.org.aodn.ogcapi.features.model.Link self = new au.org.aodn.ogcapi.features.model.Link();

        self.rel("self");
        self.type(MediaType.APPLICATION_JSON_VALUE);
        self.href(String.format("%s/collections/%s",hostname, id));

        return self;
    }

    default au.org.aodn.ogcapi.tile.model.Link getSelfTileLink(String hostname, String id) {
        au.org.aodn.ogcapi.tile.model.Link self = new au.org.aodn.ogcapi.tile.model.Link();

        self.rel("self");
        self.type(MediaType.APPLICATION_JSON_VALUE);
        self.href(String.format("%s/collections/%s/tiles/WebMercatorQuad",hostname, id));

        return self;
    }

    default au.org.aodn.ogcapi.tile.model.Link getTileSchema(String hostname) {
        au.org.aodn.ogcapi.tile.model.Link schema = new au.org.aodn.ogcapi.tile.model.Link();
        schema.rel("http://www.opengis.net/def/rel/ogc/1.0/tiling-scheme");
        schema.type(MediaType.APPLICATION_JSON_VALUE);
        schema.href(String.format("%s/tileMatrixSets/WebMercatorQuad",hostname));

        return schema;
    }
    /**
     * Create a collection given stac model
     * @param m - Income object to be transformed
     * @return A mapped JSON which match the St
     */
    default <D extends StacCollectionModel> Collection getCollection(D m, String host) {

        ExtendedCollection collection = new ExtendedCollection();
        collection.setId(m.getUuid());
        collection.setTitle(m.getTitle());
        collection.setDescription(m.getDescription());
        collection.setItemType("Collection");

        Extent extent = new Extent();

        if(m.getExtent() != null) {
            extent.setSpatial(new ExtentSpatial());

            if(m.getExtent().getBbox() != null
                    && !m.getExtent().getBbox().isEmpty()
                    && m.getExtent().getBbox().size() > 1) {
                // The first item is the overall bbox, this is STAC spec requirement but not for
                // OGC collection, hence we remove the first item.
                extent.getSpatial().bbox(m.getExtent().getBbox().subList(1, m.getExtent().getBbox().size()));
                collection.setExtent(extent);
            }
            else {
                logger.warn("BBOX is missing for this UUID {}", m.getUuid());
            }

            extent.setTemporal(new ExtentTemporal());
            extent.getTemporal().interval(m.getExtent().getTemporal());
            collection.setExtent(extent);
        }

        if(m.getLinks() != null) {
            // Convert object type.
            collection.setLinks(
                    m.getLinks()
                            .stream()
                            .map(l -> new Link()
                                .href(l.getHref())
                                .type(l.getType())
                                .rel(l.getRel())
                                .title(l.getTitle())
                            )
                    .collect(Collectors.toList()));
        }

        if (m.getSummaries() != null && m.getSummaries().getStatus() != null) {
            collection.getProperties().put(CollectionProperty.STATUS, m.getSummaries().getStatus());
        }

        return collection;
    }
}
