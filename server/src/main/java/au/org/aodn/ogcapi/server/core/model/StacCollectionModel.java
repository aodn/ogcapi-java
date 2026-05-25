package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.stac.model.AssetModel;
import au.org.aodn.stac.model.ContactsModel;
import au.org.aodn.stac.model.ExtentModel;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.SummariesModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * This is used to map the json from Elastic search to object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StacCollectionModel {

    protected String description;
    protected String type;
    protected ExtentModel extent;
    protected SummariesModel summaries;
    protected List<LinkModel> links;
    protected List<ContactsModel> contacts;
    protected List<ThemesModel> themes;
    protected String license;
    protected Map<String, AssetModel> assets;

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
