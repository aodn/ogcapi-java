package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.features.model.Link;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToCollections implements Converter<ElasticSearch.SearchResult, Collections> {

    @Value("${api.host}")
    protected String hostname;

    @Override
    public Collections convert(ElasticSearch.SearchResult model) {
        List<Collection> collections = model.getCollections().stream()
                .map(m -> getCollection(m, hostname))
                .collect(Collectors.toList());

        ExtendedCollections result = new ExtendedCollections();
        result.setTotal(model.getTotal());
        result.setSearchAfter(model.getSortValues());
        result.setCollections(collections);

        return result;
    }
}
