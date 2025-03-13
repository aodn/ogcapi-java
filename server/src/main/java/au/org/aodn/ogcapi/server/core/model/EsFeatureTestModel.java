package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
@Getter
@Setter
public class EsFeatureTestModel {
    protected String type;
    protected EsPointTestModel geometry;
    protected Map<String, Object> properties;

    public FeatureGeoJSON toFeatureGeoJSON() {
        FeatureGeoJSON f = new FeatureGeoJSON();
        f.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        f.setGeometry(geometry.toPointGeoJSON());
        f.setProperties(properties);
        return f;
    }
}
