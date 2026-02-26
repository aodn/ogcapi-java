package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsDefaultParam;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wps.WpsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeoServerConfig {

    @ConditionalOnMissingBean(name = "pretendUserEntity")
    @Bean("pretendUserEntity")
    public HttpEntity<?> createPretendUserEntity() {
        // Some server do not allow program to scrap the content, so we need to pretend to be a client
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        return new HttpEntity<>(headers);
    }

    @Bean
    public WfsServer createWfsServer(Search search,
                                     RestTemplate restTemplate,
                                     RestTemplateUtils restTemplateUtils,
                                     @Qualifier("pretendUserEntity") HttpEntity<?> entity,
                                     WfsDefaultParam wfsDefaultParam) {
        return new WfsServer(search, restTemplate, restTemplateUtils, entity, wfsDefaultParam);
    }

    @Bean
    public WmsServer createWmsServer(Search search, @Lazy WfsServer wfsServer, @Qualifier("pretendUserEntity") HttpEntity<?> entity) {
        return new WmsServer(search, wfsServer, entity);
    }

    @Bean
    public WpsServer createWpsServer(WmsServer wmsServer,
                                     WfsServer wfsServer,
                                     RestTemplate restTemplate,
                                     @Qualifier("pretendUserEntity") HttpEntity<?> entity) {
        return new WpsServer(wmsServer, wfsServer, restTemplate, entity);
    }
}
