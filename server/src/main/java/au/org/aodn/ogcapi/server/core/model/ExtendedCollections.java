package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.Collections;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Provide extension field in the return structure no part of the OGC standard
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Setter
@Getter
public class ExtendedCollections extends Collections {
    @JsonProperty("total")
    Long total = 0L;

    // This is elastic specify for paging
    // check searchAfter for elastic if not sure
    @JsonProperty("search_after")
    protected List<String> searchAfter;
}
