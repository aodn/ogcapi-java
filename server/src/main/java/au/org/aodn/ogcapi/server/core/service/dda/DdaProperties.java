package au.org.aodn.ogcapi.server.core.service.dda;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "data-discovery-ai")
public record DdaProperties(
        String host,
        @DefaultValue("api/v1/ml/manage/info") String infoPath
) {
}
