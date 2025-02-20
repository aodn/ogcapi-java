package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.tile.model.TileSet;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mapstruct.Mapper;
import org.opengis.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToTileSetWmWGS84Q implements Converter<ElasticSearch.SearchResult<StacCollectionModel>, TileSet> {

    @Value("${api.host}")
    protected String hostname;

    protected static class TileSetWorldMercatorWGS84Quad extends TileSet {

        @JsonProperty("dataType")
        public String getDataType2() { return "vector"; }

        @JsonProperty("crs")
        public String getCrs2() { return CQLCrsType.EPSG4326.url; }
    }

    @Override
    public TileSet convert(ElasticSearch.SearchResult<StacCollectionModel> from, Filter noUse) {
        TileSetWorldMercatorWGS84Quad tileSet = new TileSetWorldMercatorWGS84Quad();

        tileSet.setTileMatrixSetURI("http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldMercatorWGS84Quad");
        tileSet.addLinksItem(getTileSchema(hostname));

        for(StacCollectionModel s : from.getCollections()) {
            tileSet.addLinksItem(getSelfTileLink(hostname, s.getUuid()));

        }

        return tileSet;
    }
}
