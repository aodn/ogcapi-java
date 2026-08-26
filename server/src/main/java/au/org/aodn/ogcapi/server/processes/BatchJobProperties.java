package au.org.aodn.ogcapi.server.processes;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.batch.job")
public record BatchJobProperties(
        String queue,
        String definition,
        String childQueue
) {
    public BatchJobProperties {
        if (childQueue == null || childQueue.isBlank()) {
            childQueue = queue;
        }
    }
}
