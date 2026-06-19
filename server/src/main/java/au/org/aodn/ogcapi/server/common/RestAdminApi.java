package au.org.aodn.ogcapi.server.common;


import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController("CommonRestAdminApi")
@RequestMapping(value="/api/v1/ogc/admin")
public class RestAdminApi {
    @Autowired
    protected RestAdminService restAdminService;

    /**
     * Explain the detail relevance score of a search query
     * Internal debugging/troubleshooting usage only
     * The parameters should be the same with /collections endpoint in RestApi
     * */
    @GetMapping(path="/explain")
    public ResponseEntity<JsonNode> getExplainByParameters(
            @Parameter(in = ParameterIn.QUERY, description = "Keyword search terms")
            @Valid @RequestParam(value = "q", required = false) List<String> q,
            @Parameter(in = ParameterIn.QUERY, description = "Filter expression")
            @RequestParam(value = "filter", required = false) String filter,
            @Parameter(in = ParameterIn.QUERY, description = "Properties used by the production search request")
            @Valid @RequestParam(value = "properties", required = false) List<String> properties,
            @Size(min = 1)
            @Parameter(in = ParameterIn.QUERY, description = "Sort by a valid CQL property")
            @Valid @RequestParam(value = "sortby", required = false, defaultValue = "-score,-rank") String sortBy,
            @Parameter(in = ParameterIn.QUERY, description = "Coordinate system")
            @RequestParam(value = "crs", required = false, defaultValue = "https://epsg.io/4326") String crs,
            @Parameter(in = ParameterIn.QUERY, description = "Filter language")
            @RequestParam(value = "filter-lang", required = false, defaultValue = "cql-text") String filterLang
    ) throws Exception {
        if (restAdminService.isElasticsearchExplainEnabled()) {
            return ResponseEntity.notFound().build();
        }

        CQLCrsType convertedCrs = validateFilterAndCrs(filterLang, crs);
        return ResponseEntity.ok(restAdminService.explainByParameters(
                q,
                filter,
                properties,
                sortBy,
                convertedCrs));
    }
    /**
     * Explain an uuid matches a search query or not
     * Internal debugging/troubleshooting usage only
     * The parameters should be the same with /collections endpoint in RestApi
     * */
    @GetMapping(path="/explain/{uuid}")
    public ResponseEntity<JsonNode> getExplainByParametersUuid(
            @Parameter(in = ParameterIn.PATH, description = "Elasticsearch document id")
            @PathVariable String uuid,
            @Parameter(in = ParameterIn.QUERY, description = "Keyword search terms")
            @Valid @RequestParam(value = "q", required = false) List<String> q,
            @Parameter(in = ParameterIn.QUERY, description = "Filter expression")
            @RequestParam(value = "filter", required = false) String filter,
            @Parameter(in = ParameterIn.QUERY, description = "Properties used by the production search request")
            @Valid @RequestParam(value = "properties", required = false) List<String> properties,
            @Size(min = 1)
            @Parameter(in = ParameterIn.QUERY, description = "Sort by a valid CQL property")
            @Valid @RequestParam(value = "sortby", required = false, defaultValue = "-score,-rank") String sortBy,
            @Parameter(in = ParameterIn.QUERY, description = "Coordinate system")
            @RequestParam(value = "crs", required = false, defaultValue = "https://epsg.io/4326") String crs,
            @Parameter(in = ParameterIn.QUERY, description = "Filter language")
            @RequestParam(value = "filter-lang", required = false, defaultValue = "cql-text") String filterLang
    ) throws Exception {
        if (restAdminService.isElasticsearchExplainEnabled()) {
            return ResponseEntity.notFound().build();
        }

        CQLCrsType convertedCrs = validateFilterAndCrs(filterLang, crs);
        return ResponseEntity.ok(restAdminService.explainByUuid(
                uuid,
                q,
                filter,
                properties,
                sortBy,
                convertedCrs));
    }

    protected CQLCrsType validateFilterAndCrs(String filterLang, String crs) {
        boolean isCqlFilter = "cql-text".equals(filterLang);
        CQLCrsType convertedCrs = CQLCrsType.convertFromUrl(crs);

        if (!isCqlFilter || convertedCrs != CQLCrsType.EPSG4326) {
            List<String> reasons = new ArrayList<>();

            if (!isCqlFilter) {
                reasons.add("Unknown filter language, support cql-text only");
            }

            if (convertedCrs != CQLCrsType.EPSG4326) {
                reasons.add("Unknown crs, support EPSG4326 only");
            }

            throw new NotImplementedException(String.join(",", reasons));
        }
        return convertedCrs;
    }
}
