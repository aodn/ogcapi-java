package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.CacheNoLandGeometry;
import org.ehcache.config.builders.*;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_WMS_MAP_TILE = "cache-wms-map_tile";
    public static final String GET_CAPABILITIES_WMS_LAYERS = "get-capabilities-wms-layers";
    public static final String DOWNLOADABLE_FIELDS = "downloadable-fields";
    public static final String ALL_NO_LAND_GEOMETRY = "all-noland-geometry";
    public static final String ALL_PARAM_VOCABS = "parameter-vocabs";
    public static final String ELASTIC_SEARCH_UUID_ONLY = "elastic-search-uuid-only";

    @Bean
    public CacheNoLandGeometry createCacheNoLandGeometry() {
        return new CacheNoLandGeometry();
    }

    @Bean
    public JCacheCacheManager cacheManager() throws IOException {

        // Create a temporary directory for EhCache disk storage
        Path tempDir = Files.createTempDirectory("ehcache-elastic-collection-list");
        File storagePath = tempDir.toFile();
        storagePath.deleteOnExit(); // Mark the directory for deletion on JVM exit

        org.ehcache.config.Configuration config = ConfigurationBuilder
                .newConfigurationBuilder()
                .withService(new org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration(storagePath))
                .withCache(CACHE_WMS_MAP_TILE,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                        Object.class, byte[].class,
                                        ResourcePoolsBuilder.newResourcePoolsBuilder()
                                                .heap(100, MemoryUnit.MB)
                                                .disk(10, MemoryUnit.GB, true)
                                )
                                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache(ALL_NO_LAND_GEOMETRY,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(1)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache(ALL_PARAM_VOCABS,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(10)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache(DOWNLOADABLE_FIELDS,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(200)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache(ELASTIC_SEARCH_UUID_ONLY,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(200)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5)))
                )
                .withCache(GET_CAPABILITIES_WMS_LAYERS,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(20)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .build();


        // Get EhcacheCachingProvider
        EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider();

        // Create JCache manager from EhCache config
        CacheManager jCacheManager = provider.getCacheManager(
                provider.getDefaultURI(),
                config
        );

        return new JCacheCacheManager(jCacheManager);
    }
}
