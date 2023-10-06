package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StacCollectionModel {

    protected String title;
    protected String description;
    protected String type;
    protected ExtentModel extent;

    @JsonProperty("id")
    protected UUID uuid;

    @JsonProperty("stac_version")
    protected String stacVersion;

    @JsonProperty("stac_extensions")
    protected List<String> stacExtensions;

    public String getTitle() {
        return title;
    }

}
