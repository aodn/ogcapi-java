package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Per-user admission limits for dataset downloads. A user, identified by the recipient
 * email, may have at most {@code maxConcurrent} downloads in flight at once; anything
 * beyond that is held in memory and released as slots free rather than rejected.
 */
@ConfigurationProperties(prefix = "aws.batch.job.user-limit")
public record DownloadLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int maxConcurrent,
        /** Release loop period, and the time-to-live of the cached in-flight snapshot. */
        @DefaultValue("15s") Duration releaseInterval,
        /** A download held longer than this is abandoned; its job id then reports as not found. */
        @DefaultValue("24h") Duration maxHoldAge,
        /** Safety valve so a runaway client cannot grow the hold queue without bound. */
        @DefaultValue("1000") int maxHeldTotal
) {
}
