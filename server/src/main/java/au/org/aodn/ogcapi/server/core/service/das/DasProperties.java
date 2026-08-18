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
        /*
         * Ceiling on a whole SSE exchange (not an idle timeout like readTimeout): the estimate
         * stream stays open for as long as DAS takes to compute, so this only exists to stop a
         * silently-hung DAS from pinning a worker thread forever. Well past any estimate a user
         * would still be waiting for.
         */
        @DefaultValue("20m") Duration sseReadTimeout
) {
}
