package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureProperty;
import au.org.aodn.ogcapi.server.core.util.DatasetSummarizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DatasetSearchResult {

    private final FeatureCollectionGeoJSON dataset;

    public DatasetSearchResult() {
        this.dataset = new FeatureCollectionGeoJSON();
        initDataset();
    }

    private void initDataset() {
        dataset.setType(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION);
        dataset.setFeatures(new ArrayList<>());
    }

    public FeatureCollectionGeoJSON getSummarizedDataset() {
        var summarizer = new DatasetSummarizer(dataset);
        summarizer.summarizeDataset();
        return summarizer.getSummarizedDataset();
    }

    public void addDatum(DatumModel datum) {

        if (datum == null) {
            throw new IllegalArgumentException("Datum cannot be null");
        }

        var feature = new FeatureGeoJSON();
        feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        var geometry = new PointGeoJSON();
        geometry.setType(PointGeoJSON.TypeEnum.POINT);
        var coordinates = new ArrayList<BigDecimal>();

        // Don't use null checks here because it is a list and even if it is null,
        // it still needs "null" to occupy the space
        coordinates.add(datum.getLongitude());
        coordinates.add(datum.getLatitude());

        geometry.setCoordinates(coordinates);
        feature.setGeometry(geometry);

        // Please add more properties if needed
        Map<String, Object> properties = new HashMap<>();
        properties.put(FeatureProperty.TIME.getValue(), datum.getTime());
        properties.put(FeatureProperty.DEPTH.getValue(), datum.getDepth());
        properties.put(FeatureProperty.COUNT.getValue(), datum.getCount());

        feature.setProperties(properties);
        dataset.getFeatures().add(feature);
    }

}
