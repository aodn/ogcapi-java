package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.tile.model.InlineResponse2002;
import au.org.aodn.ogcapi.tile.model.TileSetItem;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToInlineResponse2002 implements Converter<List<StacCollectionModel>, InlineResponse2002> {

    @Value("${api.host}")
    protected String hostname;

    @Override
    public InlineResponse2002 convert(List<StacCollectionModel> model) {
        List<TileSetItem> items = model.stream()
                .map(m -> {
                    TileSetItem item = new TileSetItem();
                    item.setTitle(m.getTitle());
                    item.setCrs(CQLCrsType.EPSG4326.url);
                    item.setDataType("vector");

                    // From spec https://docs.ogc.org/is/20-057/20-057.html#toc33
                    // Links to related resources. A 'self' link to the tileset as well as a
                    // 'http://www.opengis.net/def/rel/ogc/1.0/tiling-scheme' link to a definition
                    // of the TileMatrixSet are required.
                    item.addLinksItem(getSelfTileLink(hostname, m.getUuid()));
                    item.addLinksItem(getTileSchema(hostname));

                    return item;
                })
                .collect(Collectors.toList());

        InlineResponse2002 response = new InlineResponse2002();
        response.setTilesets(items);

        return response;
    }
}
