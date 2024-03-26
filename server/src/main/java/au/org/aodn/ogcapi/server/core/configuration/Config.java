package au.org.aodn.ogcapi.server.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableCaching
@EnableScheduling
public class Config {

    @Autowired
    ObjectMapper mapper;

    @PostConstruct
    public void init() {
        // configure ObjectMapper to exclude null fields while serializing
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public RestTemplate createRestTemplate() {
        return new RestTemplate();
    }
}
