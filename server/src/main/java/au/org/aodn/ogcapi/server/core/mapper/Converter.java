package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Extent;
import au.org.aodn.ogcapi.features.model.ExtentSpatial;
import au.org.aodn.ogcapi.features.model.ExtentTemporal;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import org.springframework.http.MediaType;

@FunctionalInterface
public interface Converter<F, T> {
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
     * @param m
     * @return
     */
    default Collection getCollection(StacCollectionModel m, String host) {

        Collection collection = new Collection();
        collection.setId(m.getUuid());
        collection.setTitle(m.getTitle());
        collection.setDescription(m.getDescription());
        collection.setItemType("Collection");

        Extent extent = new Extent();

        if(m.getExtent() != null) {
            extent.setSpatial(new ExtentSpatial());

            if(m.getExtent().getBbox() != null) {
                // The first item is the overall bbox, this is requirement from STAC but not for
                // OGC collection, hence we remove the first item.
                extent.getSpatial().bbox(m.getExtent().getBbox().subList(1, m.getExtent().getBbox().size()));
                collection.setExtent(extent);
            }

            extent.setTemporal(new ExtentTemporal());
            extent.getTemporal().interval(m.getExtent().getTemporal());
            collection.setExtent(extent);
        }





        return collection;
    }
}
