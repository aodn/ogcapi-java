package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DownloadableFieldModel {
    public enum Dimension {
        range,
        single
    }

    @JsonProperty("label")
    private String label;

    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;
    // Indicate if this a single time point or range?
    @Builder.Default
    @JsonProperty("view_dimension")
    private Dimension viewDimension = Dimension.range;
}
