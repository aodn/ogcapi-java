package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordSuggestDTO {
    private List<String> abstractPhrases;

    // https://stackoverflow.com/questions/37010891/how-to-map-a-nested-value-to-a-property-using-jackson-annotations
    @JsonProperty("record_suggest")
    private void unpackRecordSuggestNode(Map<String, List<String>> recordSuggest) {
        this.abstractPhrases = recordSuggest.get("abstract_phrases");
    }
}
