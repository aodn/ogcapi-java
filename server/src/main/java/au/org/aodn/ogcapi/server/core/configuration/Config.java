package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
public class Config implements WebMvcConfigurer {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    public void initConstrucUtils(ObjectMapper mapper) {
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

    /**
     * Configure async support timeout for streaming downloads
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(1200000); // 20 minutes for streaming downloads
    }
}
