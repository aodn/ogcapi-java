package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WFSFieldModel {

    @JsonProperty("typename")
    private String typename;

    @JsonProperty("fields")
    private List<Field> fields;

    @Data
    @Builder
    public static class Field {
        @JsonProperty("label")
        private String label;

        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;
    }
}
