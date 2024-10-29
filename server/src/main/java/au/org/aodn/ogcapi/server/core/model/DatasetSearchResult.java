package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.*;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureType;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DatasetSearchResult implements IFeatureSearchResult{

    private final FeatureType featureType;


    @Getter
    private final FeatureCollectionGeoJSON dataset;

    public DatasetSearchResult() {
        this.featureType = FeatureType.DATASET;
        this.dataset = new FeatureCollectionGeoJSON();
        initDataset();
    }


    @Override
    public FeatureType getFeatureType() {
        return featureType;
    }

    private void initDataset() {
        dataset.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
        dataset.setFeatures(new ArrayList<>());
    }

    public void addRecord(DataRecordModel record) {

        if (record == null) {
            throw new IllegalArgumentException("Record cannot be null");
        }

        var feature = new FeatureGeoJSON();
        feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        var geometry = new PointGeoJSON();
        geometry.setType(PointGeoJSON.TypeEnum.POINT);
        var coordinates = new ArrayList<BigDecimal>();

        // Don't use null checks here because it is a list and even if it is null,
        // it still needs "null" to occupy the space
        coordinates.add(record.getLongitude());
        coordinates.add(record.getLatitude());

        geometry.setCoordinates(coordinates);
        feature.setGeometry(geometry);

        // Please add more properties if needed
        Map<String, Object> properties = new HashMap<>();
        properties.put(FeatureProperty.TIME.getValue(), record.getTime());
        properties.put(FeatureProperty.DEPTH.getValue(), record.getDepth());
        properties.put(FeatureProperty.COUNT.getValue(), record.getCount());

        feature.setProperties(properties);
        dataset.getFeatures().add(feature);
    }

}
