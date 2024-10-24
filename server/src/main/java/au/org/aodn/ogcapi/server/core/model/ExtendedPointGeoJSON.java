package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ExtendedPointGeoJSON extends PointGeoJSON {

    private final Map<String, Object> properties;

    public ExtendedPointGeoJSON() {
        super();
        properties = new HashMap<>();

    }

    public void addProperty(String property, Object value) {
        if (properties.get(property) != null) {
            throw new IllegalArgumentException("Property " + property + " already exists");
        }
        properties.put(property, value);
    }
}
