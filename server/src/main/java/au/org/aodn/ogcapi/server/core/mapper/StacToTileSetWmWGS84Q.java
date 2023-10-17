package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.tile.model.AllOftileSetDataType;
import au.org.aodn.ogcapi.tile.model.TileSet;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToTileSetWmWGS84Q implements Converter<List<StacCollectionModel>, TileSet> {

    @Value("${api.host}")
    protected String hostname;

    protected class TileSetWorldMercatorWGS84Quad extends TileSet {

        @JsonProperty("dataType")
        public String getDataType2() { return "vector"; }

        @JsonProperty("crs")
        public String getCrs2() { return CQLCrsType.EPSG4326.url; }
    }

    @Override
    public TileSet convert(List<StacCollectionModel> from) {
        TileSetWorldMercatorWGS84Quad tileSet = new TileSetWorldMercatorWGS84Quad();

        tileSet.setTileMatrixSetURI("http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldMercatorWGS84Quad");
        tileSet.addLinksItem(getTileSchema(hostname));

        for(StacCollectionModel s : from) {
            tileSet.addLinksItem(getSelfLink(hostname, s.getUuid()));

        }

        return tileSet;
    }
}
