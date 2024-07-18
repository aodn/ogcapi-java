package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

/**
 * This is used to map the json from Elastic search to object
 */
@Data
@Builder
public class StacCollectionModel {

    protected String description;
    protected String type;
    protected ExtentModel extent;
    protected SummariesModel summaries;
    protected List<LinkModel> links;
    protected List<ContactModel> contacts;
    protected List<ThemeModel> themes;
    protected String license;
    @JsonProperty("sci:citation")
    protected String citation;

    @Getter
    protected String title;

    @JsonProperty("id")
    protected String uuid;

    @JsonProperty("stac_version")
    protected String stacVersion;

    @JsonProperty("stac_extensions")
    protected List<String> stacExtensions;

}
