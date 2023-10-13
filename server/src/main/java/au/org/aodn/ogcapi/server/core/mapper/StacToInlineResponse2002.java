package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;

import au.org.aodn.ogcapi.tile.model.DatasetVectorGetTileSetsList200Response;
import au.org.aodn.ogcapi.tile.model.TileSetItem;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToInlineResponse2002 implements Converter<List<StacCollectionModel>, DatasetVectorGetTileSetsList200Response> {

    public DatasetVectorGetTileSetsList200Response convert(List<StacCollectionModel> model) {
        List<TileSetItem> items = model.stream()
                .map(m -> {
                    TileSetItem item = new TileSetItem();
                    item.setTitle(m.getTitle());

                    return item;
                })
                .collect(Collectors.toList());

        DatasetVectorGetTileSetsList200Response response = new DatasetVectorGetTileSetsList200Response();
        response.setTilesets(items);

        return response;
    }
}
