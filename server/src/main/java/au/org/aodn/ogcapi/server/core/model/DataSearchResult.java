package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureProperty;
import au.org.aodn.ogcapi.server.core.util.DatasetSummarizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static au.org.aodn.ogcapi.server.core.util.CommonUtils.safeGet;

public class DataSearchResult {

    private final FeatureCollectionGeoJSON dataset;

    public DataSearchResult() {
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

    public void addDatum(StacItemModel datum) {

        if (datum == null) {
            throw new IllegalArgumentException("Datum cannot be null");
        }

        FeatureGeoJSON feature = new FeatureGeoJSON();
        feature.setType(FeatureGeoJSON.TypeEnum.FEATURE);

        var geometry = new PointGeoJSON();
        geometry.setType(PointGeoJSON.TypeEnum.POINT);

        List<BigDecimal> coordinates = new ArrayList<>();

        // Don't use null checks here because it is a list and even if it is null,
        // it still needs "null" to occupy the space
        safeGet(() -> ((Map<?,?>)datum.getGeometry().get("geometry")).get("coordinates"))
                .filter(item -> item instanceof List)
                .map(item -> (List<?>)item)
                .ifPresent(item -> {
                    if(item.size() == 2) {
                        coordinates.add(new BigDecimal(item.get(0).toString()));
                        coordinates.add(new BigDecimal(item.get(1).toString()));
                    }
                });


        geometry.setCoordinates(coordinates);
        feature.setGeometry(geometry);

        // Please add more properties if needed
        Map<String, Object> properties = new HashMap<>();

        safeGet(() -> ((Map<?,?>)datum.getGeometry().get("properties")).get("depth"))
                .ifPresent(val -> properties.put(FeatureProperty.DEPTH.getValue(), val));

        safeGet(() -> (datum.getProperties().get("time")))
                .ifPresent(val -> properties.put(FeatureProperty.TIME.getValue(), val));

        safeGet(() -> (datum.getProperties().get("count")))
                .ifPresent(val -> properties.put(FeatureProperty.COUNT.getValue(), val));


        feature.setProperties(properties);
        dataset.getFeatures().add(feature);
    }

}
