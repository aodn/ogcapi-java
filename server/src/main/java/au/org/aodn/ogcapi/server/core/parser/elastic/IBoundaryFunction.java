package au.org.aodn.ogcapi.server.core.parser.elastic;


import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.FunctionImpl;
import org.geotools.filter.capability.FunctionNameImpl;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IBoundaryFunction extends FunctionImpl {

    private static Map<String, Geometry> loadStaticMap(String path, String keyField) {
        final Map<String, Geometry> map = new HashMap<>();
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode features = root.get("features");
            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode properties = feature.get("properties");
                    if (properties != null) {
                        JsonNode objectId = properties.get(keyField);
                        if (objectId != null) {
                            String key = objectId.asText();
                            JsonNode geometry = feature.get("geometry");
                            if (geometry != null) {
                                String geoJson = mapper.writeValueAsString(geometry);
                                GeometryUtils.readGeometry(geoJson).ifPresent(geo -> map.put(key, geo));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load {}", path, e);
        }
        return map;
    }

    static final Map<String, Geometry> CORAL_ATLAS = loadStaticMap("static/api/v1/ogc/ext/static/Allen_Coral_Atlas.json", "OBJECTID");

    static final Map<String, Geometry> AUZ_MARINE_PARK = loadStaticMap("static/api/v1/ogc/ext/static/Australian_Marine_Parks_boundaries.json", "OBJECTID");

    static final Map<String, Geometry> MEOW = loadStaticMap("static/api/v1/ogc/ext/static/Meow.json", "ECO_CODE");

    static Geometry getGeoJsonFromMap(String mapName, String key) {
        return switch (mapName) {
            case "CORAL_ATLAS" -> CORAL_ATLAS.get(key);
            case "AUSTRALIAN_MARINE_PARKS" -> AUZ_MARINE_PARK.get(key);
            case "MEOW" -> MEOW.get(key);
            default -> null;
        };
    }

    public static final FunctionName NAME = new FunctionNameImpl(
            "IBOUNDARY",
            Geometry.class,
            FunctionNameImpl.parameter("name", String.class),
            FunctionNameImpl.parameter("id", String.class)  // optional
    );

    public IBoundaryFunction(List<Expression> parameters, Literal fallback) {
        this.setName("IBOUNDARY");
        this.setParameters(parameters);
        this.setFallbackValue(fallback);
    }

    @Override
    public Object evaluate(Object feature) {
        String name = getParameters().get(0).evaluate(feature, String.class);
        String id = getParameters().get(1).evaluate(feature, String.class);

        return getGeoJsonFromMap(name, id);
    }

    @Override
    public FunctionName getFunctionName() {
        return NAME;
    }
}
