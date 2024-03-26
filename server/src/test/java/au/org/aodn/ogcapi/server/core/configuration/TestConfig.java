package au.org.aodn.ogcapi.server.core.configuration;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TestConfig {

    @Bean
    @Primary
    public RestTemplate createMockRestTemplate() {
        return Mockito.mock(RestTemplate.class);
    }
}
