package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.CategoryVocabModel;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController("CommonRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext")
public class CommonRestExtApi {
    @Value("${api.vocabs:https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-parameter-category-vocabulary}")
    protected String vocabApi;
    @Autowired
    protected RestTemplate template;
    @Autowired
    protected Search searchService;
    @GetMapping(path="/autocomplete")
    public ResponseEntity<List<String>> getAutocompleteSuggestions(@RequestParam String input) throws java.lang.Exception {
        return searchService.getAutocompleteSuggestions(input);
    }
    /**
     * Evict cache to allow reload
     */
    @CacheEvict(value="parameter_category", allEntries = true)
    @Scheduled(fixedRateString = "${caching.parameter_category.ttl:43200000}")
    public void emptyCachedParameterCategory() {
        log.info("Evict parameter_category cache as TTL pass");
    }
    /**
     * Value cached to avoid excessive load
     * @return
     */
    @Cacheable("parameter_category")
    @GetMapping(path="/parameter/category")
    public ResponseEntity<List<CategoryVocabModel>> getParameterCategory() {
        // Loop the url until end of page, then return value.
        List<CategoryVocabModel> result = new ArrayList<>();
        boolean isEndOfPage = false;

        int i = 0;
        String url = null;

        while(!isEndOfPage) {
            try {
                url = String.format(vocabApi + "/version-2-1/concept.json?_page=%d", i++);
                log.debug("Query api -> {}", url);

                ObjectNode r = template.getForObject(url, ObjectNode.class);

                if(r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if(!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for(JsonNode j : node.get("items")) {
                            Map<String, String> broader = new HashMap<>();
                            Map<String, String> narrower = new HashMap<>();

                            log.debug("Processing label {}", j.get("prefLabel").get("_value").asText());
                            if(j.has("broader")) {
                                for (JsonNode b : j.get("broader")) {
                                    if (b.has("prefLabel") && b.has("_about")) {
                                        broader.put(b.get("prefLabel").get("_value").asText(), b.get("_about").asText());
                                    }
                                }
                            }

                            if(j.has("narrower")) {
                                for (JsonNode b : j.get("narrower")) {
                                    if (b.has("prefLabel") && b.has("_about")) {
                                        narrower.put(b.get("prefLabel").get("_value").asText(), b.get("_about").asText());
                                    }
                                }
                            }

                            CategoryVocabModel model = CategoryVocabModel
                                    .builder()
                                    .label(j.get("prefLabel").get("_value").asText())
                                    .definition(j.asText("definition"))
                                    .broader(broader)
                                    .narrower(narrower)
                                    .build();

                            result.add(model);
                        }
                    }
                    else {
                        isEndOfPage = true;
                    }
                }
            }
            catch(RestClientException e) {
                log.error("Fail connect {}, category return likely outdated", url);
                isEndOfPage = true;
            }
        }
        return ResponseEntity.ok(result);
    }
}
