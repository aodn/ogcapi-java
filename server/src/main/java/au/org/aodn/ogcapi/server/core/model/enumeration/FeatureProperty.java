package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum FeatureProperty {
    TIME("time"),
    DEPTH("depth"),
    POINT_COUNT("point_count")
    ;

    private final String value;

    FeatureProperty(String value) {
        this.value = value;
    }

}
