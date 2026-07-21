package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-access-service")
public record DasProperties(
        String host,
        String secret,
        String internal,
        @DefaultValue Tiler tiler
) {
    public record Tiler(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("30s") Duration readTimeout
    ) {}
}
