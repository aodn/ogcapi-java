package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.CacheNoLandGeometry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheNoLandGeometry createCacheNoLandGeometry() {
        return new CacheNoLandGeometry();
    }
}
