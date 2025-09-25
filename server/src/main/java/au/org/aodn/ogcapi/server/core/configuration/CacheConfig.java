package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.CacheNoLandGeometry;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheNoLandGeometry createCacheNoLandGeometry() {
        return new CacheNoLandGeometry();
    }

    @Bean
    public JCacheCacheManager cacheManager() {
        // Create a JCache CacheManager backed by EhCache
        javax.cache.CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();

        // Configure the "elastic-collection-list" cache
        MutableConfiguration<Object, Object> cacheConfiguration = new MutableConfiguration<>()
                .setTypes(Object.class, Object.class) // Generic key-value types
                .setStoreByValue(false)
                .setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 24))) // TTL of 60 minutes
                .setStatisticsEnabled(true) // Optional: Enable cache statistics
                .setManagementEnabled(true); // Optional: Enable cache management

        // Register the cache
        cacheManager.createCache("all-noland-geometry", cacheConfiguration);
        cacheManager.createCache("parameter-vocabs", cacheConfiguration);
        cacheManager.createCache("downloadable-fields", cacheConfiguration);

        // Return Spring's JCacheCacheManager
        return new JCacheCacheManager(cacheManager);
    }
}
