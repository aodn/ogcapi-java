package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabDto {
    // properties are extendable (e.g platformVocabs, organisationVocabs etc.), currently just parameterVocabs.
    @JsonProperty("parameter_vocab")
    VocabModel parameterVocabModel;

    @JsonProperty("platform_vocab")
    VocabModel platformVocabModel;

    @JsonProperty("organisation_vocab")
    VocabModel organisationVocabModel;
}
