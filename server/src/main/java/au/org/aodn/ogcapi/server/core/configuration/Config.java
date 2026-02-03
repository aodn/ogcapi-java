package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
public class Config {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    public void initConstructUtils(ObjectMapper mapper) {
        ConstructUtils.setObjectMapper(mapper);
    }

    @PostConstruct
    public void init() {
        // configure ObjectMapper to exclude null fields while serializing
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1200000); // 20 minutes connection timeout
        factory.setReadTimeout(1200000);    // 20 minutes read timeout for large downloads

        return new RestTemplate(factory);
    }

    @Bean
    public RestTemplateUtils createRestTemplateUtils(RestTemplate restTemplate) {
        return new RestTemplateUtils(restTemplate);
    }

    @Bean
    public GeometryUtils createGeometryUtils() {
        return new GeometryUtils();
    }
}
