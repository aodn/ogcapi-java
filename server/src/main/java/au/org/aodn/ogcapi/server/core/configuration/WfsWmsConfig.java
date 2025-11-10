package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WfsWmsConfig {

    @Bean
    public WfsServer createWfsServer(Search search,
                                     DownloadableFieldsService downloadableFieldsService,
                                     RestTemplate restTemplate,
                                     RestTemplateUtils restTemplateUtils) {
        return new WfsServer(search, downloadableFieldsService, restTemplate, restTemplateUtils);
    }

    @Bean
    public WmsServer createWmsServer() {
        return new WmsServer();
    }
}
