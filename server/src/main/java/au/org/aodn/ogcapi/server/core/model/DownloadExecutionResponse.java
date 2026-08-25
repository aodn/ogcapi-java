package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DownloadExecutionResponse(
        @JsonProperty("message") InlineValue message,
        @JsonProperty("status") InlineValue status,
        @JsonProperty("jobID") String jobId
) implements InlineResponse200 {
}
