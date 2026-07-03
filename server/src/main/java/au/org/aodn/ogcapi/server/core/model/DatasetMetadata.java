package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
public class DatasetMetadata {

    // Allows Jackson to deserialize the root JSON object directly into this wrapper class
    @JsonValue
    private Map<String, DatasetInfo> datasets;

    @JsonCreator
    public DatasetMetadata(Map<String, DatasetInfo> datasets) {
        this.datasets = datasets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatasetInfo {
        private String uuid;
        private String dname;
        private CoordinateBounds lat;
        private CoordinateBounds lng;
        private DepthBounds depth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinateBounds {
        private Double min;
        private Double max;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepthBounds {
        private Double min;
        private Double max;
        private String unit;
    }
}
