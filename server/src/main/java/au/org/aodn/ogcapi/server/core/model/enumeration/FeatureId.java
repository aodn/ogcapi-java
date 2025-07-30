package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum FeatureId {
    summary("summary"),
    downloadableFields("downloadableFields");

    private final String featureId;

    FeatureId(String id) {
        this.featureId = id;
    }
}
