package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.ALL_PARAM_VOCABS;

@Slf4j
@RestController("CommonRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext")
public class RestExtApi {
    @Autowired
    protected Search searchService;

    @Autowired
    protected RestExtService restExtService;

    @Value("${ogcapi.debug.elasticsearch-explain-enabled:false}")
    protected boolean elasticsearchExplainEnabled;

    /**
     * This call is cql aware, so if you provided the filter string, then the return value will be filtered by
     * the cql clause.
     *
     * @param input - The text for search
     * @param cql = The cql that bounded the search area
     * @return - The suggested text based on the search criteria
     * @throws java.lang.Exception - Should not happen
     */
    @GetMapping(path="/autocomplete")
    public ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(
            @RequestParam String input,
            //categories is an optional parameter, if not provided, the method will return suggestions from all categories
            @Parameter(in = ParameterIn.QUERY, description = "Filter expression")
            @RequestParam(value = "filter", required = false) String cql
    ) throws java.lang.Exception {
        return searchService.getAutocompleteSuggestions(input, cql, CQLCrsType.convertFromUrl("https://epsg.io/4326"));
    }

    @GetMapping(path="/explain")
    public ResponseEntity<JsonNode> getCollectionsExplain(
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
        if (!elasticsearchExplainEnabled) {
            return ResponseEntity.notFound().build();
        }

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

        return ResponseEntity.ok(searchService.explainByParameters(
                q,
                filter,
                properties,
                sortBy,
                convertedCrs));
    }

    @GetMapping(path="/explain/{uuid}")
    public ResponseEntity<JsonNode> getCollectionExplain(
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
        if (!elasticsearchExplainEnabled) {
            return ResponseEntity.notFound().build();
        }

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

        return ResponseEntity.ok(searchService.explainByUuid(
                uuid,
                q,
                filter,
                properties,
                sortBy,
                convertedCrs));
    }

    /**
     * Value cached to avoid excessive load
     * @return - List of Json Vocabs
     */
    @Cacheable(ALL_PARAM_VOCABS)
    @GetMapping(path="/parameter/vocabs")
    public ResponseEntity<List<JsonNode>> getParameterVocab() throws IOException {
        return ResponseEntity.ok(restExtService.groupVocabsFromEsByKey("parameter_vocab"));
    }

    @GetMapping(path="/platform/vocabs")
    public ResponseEntity<List<JsonNode>> getPlatformVocabs() throws IOException {
        return ResponseEntity.ok(restExtService.groupVocabsFromEsByKey("platform_vocab"));
    }

    @GetMapping(path="/organisation/vocabs")
    public ResponseEntity<List<JsonNode>> getOrganisationVocabs() throws IOException {
        return ResponseEntity.ok(restExtService.groupVocabsFromEsByKey("organisation_vocab"));
    }
}
