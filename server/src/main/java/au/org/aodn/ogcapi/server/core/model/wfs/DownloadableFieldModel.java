package au.org.aodn.ogcapi.server.core.model.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DownloadableFieldModel {
    @JsonProperty("label")
    private String label;

    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;
}
