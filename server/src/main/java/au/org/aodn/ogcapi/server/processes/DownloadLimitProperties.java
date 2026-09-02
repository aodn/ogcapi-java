package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Per-user concurrency limit for dataset downloads. A user, identified by the recipient
 * email, may have at most {@code maxConcurrent} downloads in flight at once; a request past
 * that is rejected outright.
 */
@ConfigurationProperties(prefix = "aws.batch.job.user-limit")
public record DownloadLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int maxConcurrent,
        /** How long the shared in-flight snapshot is reused before the queues are swept again. */
        @DefaultValue("15s") Duration refreshInterval
) {
}
