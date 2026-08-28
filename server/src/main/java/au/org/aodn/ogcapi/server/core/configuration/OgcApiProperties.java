package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "ogcapi")
public record OgcApiProperties(
        @DefaultValue Debug debug,
        @DefaultValue HttpCache httpCache
) {
    public record Debug(
            @DefaultValue("false") boolean elasticsearchExplainEnabled
    ) {
    }

    public record HttpCache(
            @DefaultValue("false") boolean enabled,
            List<Mapping> mappings
    ) {
        public HttpCache {
            if (mappings == null) {
                mappings = List.of();
            }
        }
    }

    public record Mapping(
            String path,
            int maxAgeHours,
            Map<String, String> expectedParams
    ) {
        public Mapping {
            if (expectedParams == null) {
                expectedParams = Map.of();
            }
        }

        /**
         * True when the request path equals {@code path} and the query string contains
         * exactly the keys and decoded values in {@code expectedParams} (no extras).
         */
        public boolean matches(String requestPath, Map<String, String[]> queryParams) {
            if (path == null || !path.equals(requestPath)) {
                return false;
            }
            Map<String, String[]> params = queryParams == null ? Map.of() : queryParams;
            if (expectedParams.size() != params.size()) {
                return false;
            }
            for (Map.Entry<String, String> expected : expectedParams.entrySet()) {
                String[] values = params.get(expected.getKey());
                if (values == null || values.length != 1 || !expected.getValue().equals(values[0])) {
                    return false;
                }
            }
            return true;
        }

        public String cacheControlHeader() {
            return "public, max-age=" + Math.max(0, maxAgeHours) * 3600L;
        }
    }
}
