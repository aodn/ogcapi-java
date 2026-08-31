package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Compatible download execution response with the submitted job ID, "
        + "and whether the download is waiting for a free per-user slot.")
public record DownloadExecutionResponse(
        @JsonProperty("message") InlineValue message,
        @JsonProperty("status") InlineValue status,
        @JsonProperty("jobID") String jobId,
        @Schema(description = "True when the download was accepted but is waiting for one of "
                + "this user's concurrent download slots to free. It still has a job ID and "
                + "will start on its own; nothing further is required from the caller.")
        @JsonProperty("queued") boolean queued,
        @Schema(description = "How many of this user's downloads, including this one, are "
                + "waiting ahead of it; 1 means it starts next. Omitted unless queued.")
        @JsonProperty("queuePosition") Integer queuePosition
) implements InlineResponse200 {
}
