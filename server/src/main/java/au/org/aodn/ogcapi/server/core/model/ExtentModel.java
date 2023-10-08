package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ExtentModel {
    protected List<List<BigDecimal>> bbox;

    @JsonCreator
    public ExtentModel(@JsonProperty("bbox") List<List<BigDecimal>> bbox) {
        this.bbox = bbox;
    }
}
