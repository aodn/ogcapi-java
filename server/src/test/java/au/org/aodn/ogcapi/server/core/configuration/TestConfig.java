package au.org.aodn.ogcapi.server.core.configuration;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TestConfig {

    @Bean
    @Primary
    public RestTemplate createMockRestTemplate() {
        return Mockito.mock(RestTemplate.class);
    }

    @Bean("pretendUserEntity")
    @Primary
    public HttpEntity<?> createPretendUserEntity() {
        // Satisfy depends
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        return new HttpEntity<>(headers);
    }
}
