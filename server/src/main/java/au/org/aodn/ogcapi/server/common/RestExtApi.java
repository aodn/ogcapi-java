package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController("CommonRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext")
public class RestExtApi {
    @Autowired
    protected Search searchService;

    @Autowired
    protected RestExtService restExtService;

    /**
     * This call is cql aware, so if you provided the filter string, then the return value will be filtered by
     * the cql clause.
     *
     * @param input
     * @param cql
     * @return
     * @throws java.lang.Exception
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
    /**
     * Value cached to avoid excessive load
     * @return
     */
    @Cacheable("parameter-vocabs")
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
