package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.geonetwork.GNProperties;
import au.org.aodn.ogcapi.server.core.service.geonetwork.Geonetwork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeoNetworkConfig {

    @Bean
    public Geonetwork createGeonetwork(GNProperties properties, RestTemplate template) {
        return new Geonetwork(properties, template);
    }
}
