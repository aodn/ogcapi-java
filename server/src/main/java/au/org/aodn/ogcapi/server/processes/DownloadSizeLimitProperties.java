package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Largest download we accept. The batch job writes its output to the Fargate disk, which is
 * 200 GB, so a download estimated at maxSize or more is rejected before it is submitted.
 */
@ConfigurationProperties(prefix = "aws.batch.job.size-limit")
public record DownloadSizeLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("180GB") DataSize maxSize
) {
}
