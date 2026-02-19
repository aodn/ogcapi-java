package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WfsFields {

    @JsonProperty("typename")
    private String typename;

    @JsonProperty("fields")
    private List<WfsField> fields;
}
