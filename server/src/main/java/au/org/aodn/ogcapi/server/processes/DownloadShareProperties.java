package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Which fair-share a download belongs to. A download estimated under smallMaxSize is tagged
 * small; anything else, including a download with no estimate, is tagged large. The values go
 * into the Batch shareIdentifier later, which allows only alphanumerics.
 * When enabled is true, the share is also sent as the Batch shareIdentifier. Turn it on only
 * where the queue has a fair-share scheduling policy, because AWS rejects the field on a FIFO
 * queue and requires it on a fair-share one.
 */
@ConfigurationProperties(prefix = "aws.batch.job.share")
public record DownloadShareProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50MB") DataSize smallMaxSize,
        @DefaultValue("small-downloads") String small,
        @DefaultValue("large-downloads") String large
) {
}
