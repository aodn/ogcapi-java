package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Compatible download execution response with the submitted job ID.")
public record DownloadExecutionResponse(
        @JsonProperty("message") InlineValue message,
        @JsonProperty("status") InlineValue status,
        @JsonProperty("jobID") String jobId
) implements InlineResponse200 {
}
