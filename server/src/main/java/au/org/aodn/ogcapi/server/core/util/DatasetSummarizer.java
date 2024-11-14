package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureProperty;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class DatasetSummarizer {

    private final FeatureCollectionGeoJSON dataset;

    @Getter
    private final FeatureCollectionGeoJSON summarizedDataset;

    public DatasetSummarizer(FeatureCollectionGeoJSON dataset) {
        this.dataset = dataset;
        this.summarizedDataset = new FeatureCollectionGeoJSON();
        this.summarizedDataset.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
        summarizeDataset();
    }


    public void summarizeDataset() {
        for (var feature : dataset.getFeatures()) {
            var sameLocationFeature = getSameLocationFeature(feature);
            if (sameLocationFeature == null) {
                summarizedDataset
                        .getFeatures()
                        .add(generateNewSummarizedFeature(feature));
                continue;
            }
            var newFeature = aggregateFeature(sameLocationFeature, feature);
            summarizedDataset.getFeatures().remove(sameLocationFeature);
            summarizedDataset.getFeatures().add(newFeature);
        }
    }

    @SuppressWarnings("unchecked")
    private FeatureGeoJSON aggregateFeature(FeatureGeoJSON existingFeature, FeatureGeoJSON featureToAdd) {
        var aggregatedFeature = new FeatureGeoJSON();
        aggregatedFeature.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        aggregatedFeature.setGeometry(existingFeature.getGeometry());

        try{
            var existingProperties = (Map<String, Object>) existingFeature.getProperties();

            var newTimeStr = getTime(featureToAdd);
            existingProperties = updateTimeRange(existingProperties, newTimeStr);

            var newCount = (Long) getPropertyFromFeature(featureToAdd, FeatureProperty.COUNT);
            existingProperties = updateCount(existingProperties, newCount);

            aggregatedFeature.setProperties(existingProperties);
        } catch (ClassCastException e) {
            throw new RuntimeException("Feature properties is not a map", e);
        }
        return aggregatedFeature;
    }

    private Map<String, Object> updateCount(Map<String, Object> existingProperties, Long newCount) {
        var existingCount = (Long) existingProperties.get(FeatureProperty.COUNT.getValue());
        var updatedProperties = new HashMap<>(existingProperties);
        updatedProperties.put(FeatureProperty.COUNT.getValue(), existingCount + newCount);
        return updatedProperties;
    }

    private Map<String, Object> updateTimeRange(Map<String, Object> existingProperties, String newTimeStr) {

        var startTimeStr = (String) existingProperties.get(FeatureProperty.START_TIME.getValue());
        var endTimeStr = (String) existingProperties.get(FeatureProperty.END_TIME.getValue());

        var updatedProperties = new HashMap<>(existingProperties);
        updatedProperties.remove(FeatureProperty.TIME.getValue());
        if (newTimeStr != null) {
            var newTime = LocalDate.parse(newTimeStr);
            var startTime = LocalDate.parse(startTimeStr);
            var endTime = LocalDate.parse(endTimeStr);
            if (newTime.isBefore(startTime)) {
                startTimeStr = newTimeStr;
            } else if (newTime.isAfter(endTime)) {
                endTimeStr = newTimeStr;
            }
            updatedProperties.put(FeatureProperty.START_TIME.getValue(), startTimeStr);
            updatedProperties.put(FeatureProperty.END_TIME.getValue(), endTimeStr);
        }
        return updatedProperties;
    }


    private FeatureGeoJSON generateNewSummarizedFeature(FeatureGeoJSON feature) {
        var summarizedFeature = new FeatureGeoJSON();
        summarizedFeature.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        summarizedFeature.setGeometry(feature.getGeometry());
        summarizedFeature.setProperties(feature.getProperties());
        var time = getTime(feature);
        setPropertyToFeature(feature, FeatureProperty.START_TIME , time);
        setPropertyToFeature(feature, FeatureProperty.END_TIME , time);

        return summarizedFeature;
    }

    @SuppressWarnings("unchecked")
    private void setPropertyToFeature(
            FeatureGeoJSON feature, FeatureProperty propertyType,  String value
    ) {
        try{
            var properties = (Map<String, Object>) feature.getProperties();
            properties.put(propertyType.getValue(), value);
        } catch (ClassCastException e) {
            throw new RuntimeException("Feature properties is not a map", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getPropertyFromFeature(FeatureGeoJSON feature, FeatureProperty propertyType) {
        try{
            var properties = (Map<String, Object>) feature.getProperties();
            return properties.get(propertyType.getValue());
        } catch (ClassCastException e) {
            throw new RuntimeException("Feature properties is not a map", e);
        }
    }


    private FeatureGeoJSON getSameLocationFeature(FeatureGeoJSON feature) {
        for (var f : summarizedDataset.getFeatures()) {
            try {
                var existingCoordinates = ((PointGeoJSON) f.getGeometry()).getCoordinates();
                var newCoordinates = ((PointGeoJSON) feature.getGeometry()).getCoordinates();
                if (existingCoordinates.get(0).equals(newCoordinates.get(0)) && existingCoordinates.get(1).equals(newCoordinates.get(1))) {
                    return f;
                }
            } catch (ClassCastException e) {
                throw new RuntimeException("Feature geometry is not a point", e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getTime(FeatureGeoJSON feature) {
        if (feature.getProperties() instanceof Map) {
            var properties = (Map<String, Object>) feature.getProperties();
            return (String) properties.get(FeatureProperty.TIME.getValue());
        }
        return null;
    }
}

// following are values from experience. can change
//1 decimal place: ~11.1 kilometers
//2 decimal places: ~1.1 kilometers
//3 decimal places: ~110 meters
//4 decimal places: ~11 meters
//5 decimal places: ~1.1 meters

// zoom level less than 7.3, 1 decimal place
// zoom level less than 4, integer
// zoom level less than 1.2, 10