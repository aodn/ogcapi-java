package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetModel {
    // https://github.com/radiantearth/stac-spec/blob/master/best-practices.md#list-of-asset-roles
    public enum Role {
        DATA("data"),
        METADATA("metadata"),
        THUMBNAIL("thumbnail"),
        OVERVIEW("overview"),
        VISUAL("visual"),
        DATE("date"),
        GRAPHIC("graphic"),
        DATA_MASK("data-mask"),
        SNOW_ICE("snow-ice"),
        LAND_WATER("land-water"),
        WATER_MASK("water-mask"),
        ISO_19115("iso-19115");

        private final String role;

        Role(String role) {
            this.role = role;
        }

        @Override
        public String toString() {
            return role;
        }
    }
    /**
     * REQUIRED. URI to the asset object. Relative and absolute URI are both allowed. Trailing slashes are significant.
     */
    protected String href;
    protected String title;
    protected String description;
    protected String type;
    /**
     * The semantic roles of the asset, similar to the use of rel in links.
     */
    protected Role role;
}
