package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.Collections;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ExtendedCollections extends Collections {
    @JsonProperty("total")
    Long total;
}
