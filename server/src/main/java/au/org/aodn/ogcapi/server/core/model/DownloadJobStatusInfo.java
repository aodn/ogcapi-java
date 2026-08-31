package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.processes.model.StatusInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AODN display metadata added to the standard OGC job status response.
 */
@Schema(description = "AODN download job status, extending the standard OGC StatusInfo model.")
public class DownloadJobStatusInfo extends StatusInfo {

    @JsonProperty("collection")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @Schema(description = "Display name of the requested collection.")
    private String collection;

    @JsonProperty("dataSelection")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @Schema(description = "Dataset key or data selection requested for the download.")
    private String dataSelection;

    @JsonProperty("format")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @Schema(description = "Requested output document format.")
    private String format;

    @JsonProperty("metadataUrl")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @Schema(description = "Link to the metadata page supplied when the download was submitted.")
    private String metadataUrl;

    @JsonProperty("queued")
    @Schema(description = "True while the download is waiting for one of this user's "
            + "concurrent download slots to free. Always present, so clients never have to "
            + "infer the queued state from the message text.")
    private boolean queued;

    @JsonProperty("queuePosition")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @Schema(description = "How many of this user's downloads, including this one, are waiting "
            + "ahead of it; 1 means it starts next. Omitted unless queued. This is a position, "
            + "not an estimated time: it depends on when the running downloads finish.")
    private Integer queuePosition;

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getDataSelection() {
        return dataSelection;
    }

    public void setDataSelection(String dataSelection) {
        this.dataSelection = dataSelection;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getMetadataUrl() {
        return metadataUrl;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }
}
