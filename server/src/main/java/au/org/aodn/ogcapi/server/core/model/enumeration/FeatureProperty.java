package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum FeatureProperty {
    TIME("time"),
    DEPTH("depth"),
    COUNT("count"),
    UUID("uuid"),
    GEOMETRY("geometry"),
    START_TIME("startTime"),
    END_TIME("endTime"),
    COORDINATE_ACCURACY("coordinateAccuracy");

    private final String value;

    FeatureProperty(String value) {
        this.value = value;
    }

}
