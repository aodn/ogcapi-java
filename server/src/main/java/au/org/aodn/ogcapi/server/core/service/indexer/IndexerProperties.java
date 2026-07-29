package au.org.aodn.ogcapi.server.core.service.indexer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "es-indexer")
public record IndexerProperties(
        String host,
        @DefaultValue("manage/info") String infoPath
) {
}
