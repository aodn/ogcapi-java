package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.OneOffeatureGeoJSONId;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonTypeName("UUID")
public class FeatureGeoJsonId implements OneOffeatureGeoJSONId {
    private String id;

    public FeatureGeoJsonId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "FeatureGeoJsonId{" +
                "id='" + id + '\'' +
                '}';
    }
}
