package au.org.aodn.ogcapi.server.core.model.dto;

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
public class SearchSuggestionsDto {
    private Map<String, List<String>> searchSuggestions;

    @JsonProperty("abstract_phrases")
    public List<String> getAbstractPhrases() { return searchSuggestions.get("abstract_phrases"); }

    @JsonProperty("parameter_vocabs_sayt")
    public List<String> getParameterVocabs() { return searchSuggestions.get("parameter_vocabs_sayt"); }

    @JsonProperty("platform_vocabs_sayt")
    public List<String> getPlatformVocabs() { return searchSuggestions.get("platform_vocabs_sayt"); }

    @JsonProperty("organisation_vocabs_sayt")
    public List<String> getOrganisationVocabs() { return searchSuggestions.get("organisation_vocabs_sayt"); }

    @JsonProperty("search_suggestions")
    private void setSearchSuggestions(Map<String, List<String>> searchSuggestions) {
        this.searchSuggestions = searchSuggestions;
    }
}
