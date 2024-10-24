package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum FeatureType {

    DATASET("dataset"),
    ;

    private final String value;

    FeatureType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
