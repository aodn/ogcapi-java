package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WfsWmsConfig {

    @Bean
    public WfsServer createWfsServer() {
        return new WfsServer();
    }

    @Bean
    public WmsServer createWmsServer() {
        return new WmsServer();
    }
}
