package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.CategoryVocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Use to store the logic and make it easier to test
 */
@Slf4j
@Service("CommonRestExtService")
public class RestExtService {
    @Autowired
    protected RestTemplate template;

    protected static String path = "/aodn-parameter-category-vocabulary/version-2-1/concept.json";
    protected static String leafPath = "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json";

    protected static String details = "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=%s";

    protected Function<JsonNode, String> label = (node) -> node.get("prefLabel").get("_value").asText();
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;

    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    /**
     * We want to get the list of leaf node for the API, from there we need to query individual resources to get the broadMatch value
     * this value is the link to the second level of the category
     *
     * API to the details to get the broadMatch
     * http://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/891
     *
     * @param vocabApiBase
     * @return
     */
    protected Map<String, List<CategoryVocabModel>> getLeafNodeOfParameterCategory(String vocabApiBase) {
        Map<String, List<CategoryVocabModel>> result = new HashMap<>();
        String url = String.format(vocabApiBase + leafPath);

        while (url != null) {
            log.debug("Query api -> {}", url);

            ObjectNode r = template.getForObject(url, ObjectNode.class);
            if (r != null && !r.isEmpty()) {
                JsonNode node = r.get("result");

                if (isNodeValid.apply(node, "items")) {
                    for (JsonNode j : node.get("items")) {
                        // Now we need to construct link to detail resources
                        String dl = String.format(vocabApiBase + details, about.apply(j));
                        try {
                            log.debug("Query api -> {}", dl);
                            ObjectNode d = template.getForObject(dl, ObjectNode.class);

                            if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                JsonNode target = d.get("result").get("primaryTopic");

                                CategoryVocabModel model = CategoryVocabModel
                                        .builder()
                                        .label(label.apply(target))
                                        .definition(definition.apply(target))
                                        .about(about.apply(target))
                                        .build();

                                if(target.has("broadMatch") && !target.get("broadMatch").isEmpty()) {
                                    for(JsonNode bm : target.get("broadMatch")) {
                                        if (!result.containsKey(bm.asText())) {
                                            result.put(bm.asText(), new ArrayList<>());
                                        }
                                        // We will have multiple cat under the same parent
                                        result.get(bm.asText()).add(model);
                                    }
                                }
                            }
                        }
                        catch(Exception e) {
                            log.error("Item not found in resource {}", dl);
                        }
                    }
                }

                if (!node.isEmpty() && node.has("next")) {
                    url = node.get("next").asText();
                }
                else {
                    url = null;
                }
            }
            else {
                url = null;
            }
        }
        return result;
    }

    public List<CategoryVocabModel> getParameterCategory(String vocabApiBase) {
        Map<String, List<CategoryVocabModel>> leaves = getLeafNodeOfParameterCategory(vocabApiBase);
        List<CategoryVocabModel> result = new ArrayList<>();

        String url = String.format(vocabApiBase + path);

        while (url != null) {
            try {
                log.debug("Query api -> {}", url);

                ObjectNode r = template.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {
                            List<CategoryVocabModel> broader = new ArrayList<>();
                            List<CategoryVocabModel> narrower = new ArrayList<>();

                            log.debug("Processing label {}", label.apply(j));
                            if (j.has("broader")) {
                                for (JsonNode b : j.get("broader")) {
                                    CategoryVocabModel c = null;
                                    if (b instanceof ObjectNode objectNode) {
                                        if (objectNode.has("prefLabel") && objectNode.has("_about")) {
                                            c = CategoryVocabModel
                                                    .builder()
                                                    .about(about.apply(b))
                                                    .label(label.apply(b))
                                                    .build();
                                        }
                                    }
                                    if (b instanceof TextNode textNode && textNode.asText().contains("parameter_classes")) {
                                        c = CategoryVocabModel.builder()
                                                .about(textNode.asText())
                                                .build();
                                    }
                                    broader.add(c);
                                }
                            }

                            if (j.has("narrower")) {
                                for (JsonNode b : j.get("narrower")) {
                                    if (b.has("prefLabel") && b.has("_about")) {
                                        CategoryVocabModel c = CategoryVocabModel
                                                .builder()
                                                .about(about.apply(b))
                                                .label(label.apply(b))
                                                .build();

                                        narrower.add(c);

                                        // The record comes from ardc have two levels only, so the second level for sure
                                        // is empty, but the third level info comes form another link (aka the leaves)
                                        // and therefore we can attach it to the second level to for the third.
                                        if(leaves.containsKey(about.apply(b))) {
                                            c.setNarrower(leaves.get(about.apply(b)));
                                        }
                                    }
                                }
                            }

                            CategoryVocabModel model = CategoryVocabModel
                                    .builder()
                                    .label(label.apply(j))
                                    .definition(definition.apply(j))
                                    .about(about.apply(j))
                                    .broader(broader)
                                    .narrower(narrower)
                                    .build();

                            result.add(model);
                        }
                    }

                    if (!node.isEmpty() && node.has("next")) {
                        url = node.get("next").asText();
                    }
                    else {
                        url = null;
                    }
                }
                else {
                    url = null;
                }
            } catch (RestClientException e) {
                log.error("Fail connect {}, category return likely outdated", url);
                url = null;
            }
        }

        return result;
    }
}
