package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.Constants;
import au.org.aodn.ogcapi.tile.model.InlineResponse2002;
import au.org.aodn.ogcapi.tile.model.Link;
import au.org.aodn.ogcapi.tile.model.TileSetItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToInlineResponse2002 implements Converter<List<StacCollectionModel>, InlineResponse2002> {

    @Value("${api.host}")
    protected String hostname;

    /**
     * Two parameter generated incorrectly due to open api file use enum which restricted the value within the list
     * it is better to use string in this case.
     */
    protected class TitleSetItemExt extends TileSetItem {

        @JsonProperty("crs")
        public String getNCrs() { return CQLCrsType.CRS84.url; }

        @JsonProperty("dataType")
        public String datatype() { return "vector"; }
    }

    public InlineResponse2002 convert(List<StacCollectionModel> model) {
        List<TileSetItem> items = model.stream()
                .map(m -> {
                    TitleSetItemExt item = new TitleSetItemExt();
                    item.setTitle(m.getTitle());

                    // Link to self
                    Link self = new Link();
                    self.rel("self");
                    self.type(MediaType.APPLICATION_JSON_VALUE);
                    self.href(String.format("%s/collections/%s/tiles",hostname, m.getUuid()));
                    item.addLinksItem(self);

                    return item;
                })
                .collect(Collectors.toList());

        InlineResponse2002 response = new InlineResponse2002();
        response.setTilesets(items);

        return response;
    }
}
