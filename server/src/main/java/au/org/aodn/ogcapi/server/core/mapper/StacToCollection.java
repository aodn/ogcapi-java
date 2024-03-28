package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToCollection implements Converter<StacCollectionModel, Collection> {

    @Value("${api.host}")
    protected String hostname;

    @Override
    public Collection convert(StacCollectionModel model) {
        return getCollection(model, hostname);
    }
}
