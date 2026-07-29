package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DasProperties.class)
public class Config {

    public static final String DAS_REST_TEMPLATE = "dasRestTemplate";

    @Autowired
    ObjectMapper mapper;

    @Autowired
    public void initConstructUtils(ObjectMapper mapper) {
        ConstructUtils.setObjectMapper(mapper);
    }

    @PostConstruct
    public void init() {
        // register module for json serializing
        mapper.registerModule(new JsonNullableModule());
        // Configure ObjectMapper to exclude null fields while serializing
        mapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL,
                        JsonInclude.Include.USE_DEFAULTS
                )
        );
    }

    @Bean
    public RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1200000); // 20 minutes connection timeout
        factory.setReadTimeout(1200000);    // 20 minutes read timeout for large downloads

        return new RestTemplate(factory);
    }

    @Bean(name = DAS_REST_TEMPLATE, defaultCandidate = false)
    public RestTemplate createDasRestTemplate(DasProperties dasProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(dasProperties.connectTimeout());
        factory.setReadTimeout(dasProperties.readTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add((request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.set("X-API-KEY", dasProperties.secret());
            if (dasProperties.internal() != null) {
                headers.set("x-internal-das-header-secret", dasProperties.internal());
            }
            return execution.execute(request, body);
        });
        return restTemplate;
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
