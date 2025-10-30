package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import org.mapstruct.Mapper;
import org.opengis.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Mapper(componentModel = "spring")
public abstract class StacToCollections implements Converter<ElasticSearch.SearchResult<StacCollectionModel>, Collections> {

    @Value("${api.host}")
    protected String hostname;

    @Override
    public Collections convert(ElasticSearch.SearchResult<StacCollectionModel> model, Filter filter) {

        List<Collection> collections = model.getCollections().stream()
                .map(m -> getCollection(m, filter, hostname))
                .toList();

        ExtendedCollections result = new ExtendedCollections();
        result.setTotal(model.getTotal());

        if(model.getSortValues() != null) {
            result.setSearchAfter(model.getSortValues().stream().map(String::valueOf).toList());
        }
        result.setCollections(collections);

        return result;
    }
}
