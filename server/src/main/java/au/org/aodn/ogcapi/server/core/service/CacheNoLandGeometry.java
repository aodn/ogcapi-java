package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class is used to cache some of the search result which is very expensive to transfer.
 */
@Slf4j
public class CacheNoLandGeometry {

    @Lazy
    @Autowired
    ElasticSearch elasticSearch;

    @Autowired
    @Lazy
    private CacheNoLandGeometry self;

    /**
     * Init cache after 1 second but then never call again due to Long.MAX_VALUE
     * @return A Map include the uuid and the noloand geometry
     */
    @Scheduled(initialDelay = 1000, fixedDelay = Long.MAX_VALUE)
    @Cacheable("all_noland_geometry")
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
    /**
     *  Refresh every 24 hrs
     */
    @Scheduled(initialDelay = 86400000, fixedDelay = 86400000)
    @CacheEvict(value = "all_noland_geometry", allEntries = true)
    protected void refreshAllCache() {
        // Refresh on Evict
        log.info("Evict and refresh cache");
        self.getAllNoLandGeometry();
    }
}
