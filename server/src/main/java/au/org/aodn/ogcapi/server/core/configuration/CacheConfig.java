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
                .withCache("cache-maptile",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, byte[].class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(100, MemoryUnit.MB)
                                        .disk(10, MemoryUnit.GB, true)
                                )
                                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache("all-noland-geometry",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(1)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache("parameter-vocabs",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(10)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache("downloadable-fields",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(200)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24)))
                )
                .withCache("elastic-search-uuid-only",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.heap(200)
                        ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5)))
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
