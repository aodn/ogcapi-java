package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummariesModel {
    // Do not create constructor, it will create by lombook, you can use builder() to create object.
    protected int score;
    protected String status;
    protected List<String> credits;
    protected String creation;
    protected String revision;
    protected List<List<BigDecimal>> centroid;

    @JsonProperty("proj:geometry")
    protected Map<?,?> geometry;

    @JsonProperty("temporal")
    protected List<Map<String, String>> temporal;

    protected String statement;
}
