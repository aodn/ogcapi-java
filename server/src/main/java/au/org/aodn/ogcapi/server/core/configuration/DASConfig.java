package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-access-service")
public record DASConfig(
        String host,
        String secret,
        String internal
) {}
