package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.ardc.model.ParameterVocabModel;
import au.org.aodn.ogcapi.server.ardc.model.PlatformVocabModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.ardc.service.ArdcVocabService;
import au.org.aodn.ogcapi.server.core.service.Search;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController("CommonRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext")
public class RestExtApi {
    @Value("${api.vocabs:https://vocabs.ardc.edu.au/repository/api/lda/aodn}")
    protected String vocabApiBase;

    @Autowired
    protected ArdcVocabService ardcVocabService;

    @Autowired
    protected Search searchService;
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
     * Evict cache to allow reload
     */
    @CacheEvict(value="parameter_vocabs", allEntries = true)
    @Scheduled(fixedRateString = "${caching.parameter_vocab.ttl:43200000}")
    public void emptyCachedParameterVocab() {
        log.info("Evict parameter_vocab cache as TTL pass");
    }

    /**
     * Value cached to avoid excessive load
     * @return
     */
    @Cacheable("parameter_vocabs")
    @GetMapping(path="/parameter/vocabs")
    public ResponseEntity<List<ParameterVocabModel>> getParameterVocab() {
        return ResponseEntity.ok(ardcVocabService.getParameterVocabs(vocabApiBase));
    }

    @GetMapping(path="/platform/vocabs")
    public ResponseEntity<List<PlatformVocabModel>> getPlatformVocabs() {
        return ResponseEntity.ok(ardcVocabService.getPlatformVocabs(vocabApiBase));
    }
}
