package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class DASConfig {

    @Value("${data-access-service.host}")
    public String host;
    @Value("${data-access-service.secret}")
    public String secret;
}
