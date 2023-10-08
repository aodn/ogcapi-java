package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.tile.model.InlineResponse2002;
import au.org.aodn.ogcapi.tile.model.TileSetItem;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToInlineResponse2002 implements Converter<List<StacCollectionModel>, InlineResponse2002> {

    public InlineResponse2002 convert(List<StacCollectionModel> model) {
        List<TileSetItem> items = model.stream()
                .map(m -> {
                    TileSetItem item = new TileSetItem();
                    item.setTitle(m.getTitle());

                    return item;
                })
                .collect(Collectors.toList());

        InlineResponse2002 response = new InlineResponse2002();
        response.setTilesets(items);

        return response;
    }
}
