package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.Extent;
import au.org.aodn.ogcapi.features.model.ExtentSpatial;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToCollections implements Converter<List<StacCollectionModel>, Collections> {

    @Override
    public Collections convert(List<StacCollectionModel> model) {
        List<Collection> collections = model.stream()
                .map(m -> {

                    Collection collection = new Collection();
                    collection.setId(m.getUuid());
                    collection.setTitle(m.getTitle());
                    collection.setDescription(m.getDescription());
                    collection.setItemType("Collection");

                    Extent extent = new Extent();
                    extent.setSpatial(new ExtentSpatial());
                    extent.getSpatial().bbox(m.getExtent().getBbox());
                    collection.setExtent(extent);

                    return collection;
                })
                .collect(Collectors.toList());

        Collections result = new Collections();
        result.collections(collections);

        return result;
    }
}
