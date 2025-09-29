package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum FeatureId {
    summary("summary"),
    first_data_available("first_data_available"),
    timeseries("timeseries"),
    wfs_downloadable_fields("wfs_downloadable_fields"),
    wms_downloadable_fields("wms_downloadable_fields"),
    wms_map_tile("wms_map_tile"),
    wms_map_feature("wms_map_feature");

    public final String featureId;

    FeatureId(String id) {
        this.featureId = id;
    }
}
