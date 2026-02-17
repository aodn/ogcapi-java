package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WfsField {
    @JsonProperty("label")
    private String label;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;
}
