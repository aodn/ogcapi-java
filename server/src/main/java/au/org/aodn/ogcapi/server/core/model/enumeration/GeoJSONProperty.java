package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum GeoJSONProperty {
    TIME("time"),
    ;

    private final String value;

    GeoJSONProperty(String value) {
        this.value = value;
    }

}
