package au.org.aodn.ogcapi.server.core.service.das;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-access-service")
public record DasProperties(
        String host,
        @DefaultValue("api/v1/das/manage/info") String infoPath,
        String secret,
        String internal,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("2m") Duration sseIdleTimeout
) {
}
