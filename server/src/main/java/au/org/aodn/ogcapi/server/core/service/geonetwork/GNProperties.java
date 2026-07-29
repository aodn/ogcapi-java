package au.org.aodn.ogcapi.server.core.service.geonetwork;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "geonetwork4")
public record GNProperties (
        String host,
        @DefaultValue("geonetwork/srv/api/manage/info") String infoPath
) {
}
