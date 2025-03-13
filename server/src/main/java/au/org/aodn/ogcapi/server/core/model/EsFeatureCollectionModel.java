package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class EsFeatureCollectionModel {
    protected String type;
    protected List<EsFeatureModel> features;
    protected Map<String, Object> properties;

    public FeatureCollectionGeoJSON toFeatureCollectionGeoJSON() {
        FeatureCollectionGeoJSON f = new FeatureCollectionGeoJSON();
        f.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
        f.setFeatures(features.stream().map(EsFeatureModel::toFeatureGeoJSON).toList());
        return f;
    }
}
