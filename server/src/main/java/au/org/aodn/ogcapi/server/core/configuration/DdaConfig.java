package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.dda.DdaProperties;
import au.org.aodn.ogcapi.server.core.service.dda.DdaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DdaConfig {

    @Bean
    public DdaService createDdaServer(DdaProperties properties, RestTemplate template) {
        return new DdaService(properties,template);
    }
}
