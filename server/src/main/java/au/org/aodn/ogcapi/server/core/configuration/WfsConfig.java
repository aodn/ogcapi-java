package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WfsConfig {

    @Bean
    public WfsServer createWfsServer() {
        return new WfsServer();
    }
}
