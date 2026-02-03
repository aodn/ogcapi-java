package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum FeatureId {
    summary("summary"),
    first_data_available("first_data_available"),
    timeseries("timeseries"),
    wfs_downloadable_fields("wfs_downloadable_fields"), // Query field based on pure wfs and given layer
    wms_downloadable_fields("wms_downloadable_fields"), // Query field based on value from wms describe layer query
    wms_map_tile("wms_map_tile"),
    wms_map_feature("wms_map_feature"),
    wms_layers("wms_layers"), // Get all available layers from WMS GetCapabilities
    wfs_layers("wfs_layers"); // Get all available feature types from WFS GetCapabilities

    public final String featureId;

    FeatureId(String id) {
        this.featureId = id;
    }
}
