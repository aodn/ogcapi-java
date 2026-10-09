package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Which fair-share a download belongs to. A download estimated under smallMaxSize is tagged
 * small; anything else, including a download with no estimate, is tagged large. The tag always
 * goes out as the share_identifier job parameter. When enabled is true it is also sent as the
 * Batch shareIdentifier, which allows only alphanumerics. Turn it on only where the queue has a
 * fair-share scheduling policy, because AWS rejects the field on a FIFO queue and requires it on
 * a fair-share one.
 */
@ConfigurationProperties(prefix = "aws.batch.job.share")
public record DownloadShareProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50MB") DataSize smallMaxSize,
        @DefaultValue("small") String small,
        @DefaultValue("large") String large
) {
}
