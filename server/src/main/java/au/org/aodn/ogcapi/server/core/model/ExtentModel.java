package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class ExtentModel {
    protected List<List<BigDecimal>> bbox;
    protected List<List<Date>> temporal;

    @JsonCreator
    public ExtentModel(@JsonProperty("bbox") List<List<BigDecimal>> bbox, @JsonProperty("temporal") List<List<Date>> temporal) {
        this.temporal = temporal;
        this.bbox = bbox;
    }
}
