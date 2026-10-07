package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Which fair-share a download belongs to. A download estimated under smallMaxSize is tagged
 * small; anything else, including a download with no estimate, is tagged large. The values go
 * into the Batch shareIdentifier later, which allows only alphanumerics.
 */
@ConfigurationProperties(prefix = "aws.batch.job.share")
public record DownloadShareProperties(
        @DefaultValue("50MB") DataSize smallMaxSize,
        @DefaultValue("small") String small,
        @DefaultValue("large") String large
) {
}
