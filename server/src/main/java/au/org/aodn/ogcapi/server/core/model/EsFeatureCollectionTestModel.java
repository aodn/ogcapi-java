package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class EsFeatureCollectionTestModel {
    protected String type;
    protected List<EsFeatureTestModel> features;
    protected Map<String, Object> properties;

    public FeatureCollectionGeoJSON toFeatureCollectionGeoJSON() {
        FeatureCollectionGeoJSON f = new FeatureCollectionGeoJSON();
        f.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
        f.setFeatures(features.stream().map(EsFeatureTestModel::toFeatureGeoJSON).toList());
        return f;
    }
}
