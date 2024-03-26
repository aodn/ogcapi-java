package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToCollections implements Converter<List<StacCollectionModel>, Collections> {

    @Value("${api.host}")
    protected String hostname;

    @Override
    public Collections convert(List<StacCollectionModel> model) {
        List<Collection> collections = model.stream()
                .map(m -> getCollection(m, hostname))
                .collect(Collectors.toList());

        Collections result = new Collections();
        result.collections(collections);

        return result;
    }
}
