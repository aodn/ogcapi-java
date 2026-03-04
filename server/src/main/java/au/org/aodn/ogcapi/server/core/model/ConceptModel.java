package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptModel {
    protected String id;
    protected String url;
    protected String description;
    protected String title;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("ai:description")
    protected String aiDescription;
}
