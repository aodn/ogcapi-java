package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.GeometryGeoJSON;
import au.org.aodn.ogcapi.features.model.GeometrycollectionGeoJSON;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureType;
import au.org.aodn.ogcapi.server.core.model.enumeration.GeoJSONProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;

public class DatasetSearchResult implements IFeatureSearchResult{

    private final FeatureType featureType;


    @Getter
    private final FeatureGeoJSON dataset;

    public DatasetSearchResult(String uuid) {
        this.featureType = FeatureType.DATASET;
        this.dataset = new FeatureGeoJSON();
        initDataset();
        this.dataset.setId(new FeatureGeoJsonId(uuid));
    }


    @Override
    public FeatureType getFeatureType() {
        return featureType;
    }

    private void initDataset() {
        var geoCollection = new GeometrycollectionGeoJSON();
        geoCollection.setGeometries(new ArrayList<>());
        dataset.setGeometry(geoCollection);
    }

    public void addRecord(DataRecordModel record) {
        var geoRecord = new ExtendedPointGeoJSON();
        var coordinates = new ArrayList<BigDecimal>();

        // Don't use null checks here because it is a list and even if it is null,
        // it still needs "null" to occupy the space
        coordinates.add(record.getLongitude());
        coordinates.add(record.getLatitude());
        coordinates.add(record.getDepth());

        geoRecord.setCoordinates(coordinates);

        // Please add more properties if needed
        geoRecord.addProperty(GeoJSONProperty.TIME.getValue(), record.getTime());

        setGeometry(geoRecord);
    }

    private void setGeometry(GeometryGeoJSON geometry) {
        var geoCollection = (GeometrycollectionGeoJSON) dataset.getGeometry();
        geoCollection.getGeometries().add(geometry);
    }

}
