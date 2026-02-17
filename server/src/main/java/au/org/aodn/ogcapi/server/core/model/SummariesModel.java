package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummariesModel {
    // Do not create constructor, it will create by lombook, you can use builder() to create object.
    protected int score;
    protected String status;
    protected List<String> credits;
    protected String creation;
    protected String revision;

    @JsonProperty("proj:geometry")
    protected Map<?, ?> geometry;

    @JsonProperty("proj:geometry_noland")
    protected Map<?, ?> geometryNoLand;

    @JsonProperty("temporal")
    protected List<Map<String, String>> temporal;

    @JsonProperty("update_frequency")
    protected String updateFrequency;

    @JsonProperty("dataset_group")
    protected List<String> datasetGroup;

    @JsonProperty("ai:description")
    protected String aiDescription;

    @JsonProperty("ai:update_frequency")
    protected String aiUpdateFrequency;

    @JsonProperty("scope")
    protected Map<String, String> scope;

    protected String statement;

    @JsonProperty("parameter_vocabs")
    protected List<String> parameterVocabs;
}
