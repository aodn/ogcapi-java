package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.ALL_NO_LAND_GEOMETRY;

/**
 * This class is used to cache some of the search result which is very expensive to transfer.
 */
@Slf4j
public class CacheNoLandGeometry {

    @Lazy
    @Autowired
    ElasticSearch elasticSearch;

    /**
     * Init cache after 1 second but then never call again due to Long.MAX_VALUE
     * @return A Map include the uuid and the noloand geometry
     */
    @Cacheable(ALL_NO_LAND_GEOMETRY)
    public Map<String, StacCollectionModel> getAllNoLandGeometry() {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = elasticSearch.searchCollectionBy(
                null,
                null,
                null,
                List.of(CQLFields.id.name(), CQLFields.centroid_nocache.name()),
                null,
                null,
                null,
                null);

        return result.collections
                .stream()
                .collect(Collectors.toMap(StacCollectionModel::getUuid, Function.identity()));
    }
}
