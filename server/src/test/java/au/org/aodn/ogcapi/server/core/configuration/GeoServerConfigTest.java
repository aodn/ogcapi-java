package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.DownloadWfsDataService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeoServerConfigTest {

    @Primary
    @Bean
    public DownloadWfsDataService createDownloadWfsDataService(WfsServer wfsServer,
                                                               RestTemplate restTemplate,
                                                               @Qualifier("pretendUserEntity") HttpEntity<?> pretendUserEntity,
                                                               @Value("${app.sse.chunkSize:16384}") int chunkSize) {
        // We do not add interceptor here because most of the time restTemplate is mock and cannot trigger custom interceptor
        return new DownloadWfsDataService(wfsServer, restTemplate, pretendUserEntity, chunkSize);
    }
}
