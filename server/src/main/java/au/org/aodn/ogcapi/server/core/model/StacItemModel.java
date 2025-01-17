package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StacItemModel {

    @JsonProperty("type")
    protected String type;
    /**
     * REQUIRED. Provider identifier. The ID should be unique within the Collection that contains the Item.
     */
    @JsonProperty("id")
    protected String uuid;
    /**
     * REQUIRED. Defines the full footprint of the asset represented by this item, formatted according to RFC 7946,
     * section 3.1 if a geometry is provided or section 3.2 if no geometry is provided.
     * Use to generate the vector tile, the STAC format is not optimized and hard to work with for Elastic search
     */
    @JsonProperty("geometry")
    protected Map<?,?> geometry;
    /**
     * REQUIRED if geometry is not null, prohibited if geometry is null. Bounding Box of the asset represented by
     * this Item, formatted according to RFC 7946, section 5.
     */
    @JsonProperty("bbox")
    protected List<List<BigDecimal>> bbox;
    /**
     * REQUIRED. A dictionary of additional metadata for the Item.
     */
    @JsonProperty("properties")
    protected Map<?, ?> properties;
    /**
     * REQUIRED. List of link objects to resources and related URLs. See the best practices for details on when the
     * use self links is strongly recommended.
     */
    protected List<LinkModel> links;

    protected Map<String, AssetModel> assets;
    /**
     * The id of the STAC Collection this Item references to. This field is required if a link with a collection relation type is present and is not allowed otherwise.
     */
    @JsonProperty("collection")
    protected String collection;

    @JsonProperty("stac_version")
    protected String stacVersion;

    @JsonProperty("stac_extensions")
    public String[] getStacExtension() {
        return new String[] {
                "https://stac-extensions.github.io/scientific/v1.0.0/schema.json",
                "https://stac-extensions.github.io/contacts/v0.1.1/schema.json",
                "https://stac-extensions.github.io/projection/v1.1.0/schema.json",
                "https://stac-extensions.github.io/language/v1.0.0/schema.json",
                "https://stac-extensions.github.io/themes/v1.0.0/schema.json",
                "https://stac-extensions.github.io/web-map-links/v1.2.0/schema.json"
        };
    }

}
