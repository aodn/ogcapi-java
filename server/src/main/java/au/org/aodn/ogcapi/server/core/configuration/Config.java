package au.org.aodn.ogcapi.server.core.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    @Autowired
    ObjectMapper mapper;

    @PostConstruct
    public void init() {
        // configure ObjectMapper to exclude null fields while serializing
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
