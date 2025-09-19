package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum FeatureId {
    summary("summary"),
    downloadableFields("downloadableFields"),
    first_data_available("first_data_available"),
    timeseries("timeseries"),
    wms_map_feature("wms_map_feature");

    public final String featureId;

    FeatureId(String id) {
        this.featureId = id;
    }
}
