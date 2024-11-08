package au.org.aodn.ogcapi.server.core.mapper;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import au.org.aodn.ogcapi.server.core.parser.stac.CQLToStacFilterFactory;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.mapstruct.Mapper;
import org.opengis.filter.Filter;
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
    public Collections convert(ElasticSearch.SearchResult model, Param param) {
        CQLToStacFilterFactory factory = CQLToStacFilterFactory.builder()
                .cqlCrsType(param.getCoordinationSystem())
                .build();

        Filter f = null;
        try {
            f = CompilerUtil.parseFilter(Language.CQL, param.getFilter(), factory);
        }
        catch(Exception ex) {
            // Do nothing
        }
        final Filter filter = f;

        List<Collection> collections = model.getCollections().stream()
                .map(m -> getCollection(m, filter, hostname))
                .collect(Collectors.toList());

        ExtendedCollections result = new ExtendedCollections();
        result.setTotal(model.getTotal());
        if(model.getSortValues() != null) {
            result.setSearchAfter(model.getSortValues().stream().map(String::valueOf).toList());
        }
        result.setCollections(collections);

        return result;
    }
}
