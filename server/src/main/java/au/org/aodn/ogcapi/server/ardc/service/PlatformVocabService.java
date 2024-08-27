package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.PlatformVocabModel;
import com.fasterxml.jackson.databind.JsonNode;
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


@Slf4j
@Service("PlatformVocabService")
public class PlatformVocabService {
    @Autowired
    protected RestTemplate ardcVocabRestTemplate;

    protected static String platformVocabApiPath = "/aodn-platform-category-vocabulary/version-1-2/concept.json";

    protected static String leafPath = "/aodn-platform-vocabulary/version-6-1/concept.json";

    protected static String details = "/aodn-platform-vocabulary/version-6-1/resource.json?uri=%s";

    protected Function<JsonNode, String> label = (node) -> node.get("prefLabel").get("_value").asText();
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;

    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    /**
     * We want to get the list of leaf node for the API, from there we need to query individual resources to get the broadMatch value
     * this value is the link to the second level of the vocab
     *
     * API to the details to get the broadMatch
     * http://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/891
     *
     * @param vocabApiBase
     * @return
     */
    protected Map<String, List<PlatformVocabModel>> getLeafNodeOfParameterVocab(String vocabApiBase) {
        Map<String, List<PlatformVocabModel>> results = new HashMap<>();
        String url = String.format(vocabApiBase + leafPath);

        while (url != null) {
            log.debug("Query api -> {}", url);

            ObjectNode r = ardcVocabRestTemplate.getForObject(url, ObjectNode.class);
            if (r != null && !r.isEmpty()) {
                JsonNode node = r.get("result");

                if (isNodeValid.apply(node, "items")) {
                    for (JsonNode j : node.get("items")) {
                        // Now we need to construct link to detail resources
                        String dl = String.format(vocabApiBase + details, about.apply(j));
                        try {
                            log.debug("Query api -> {}", dl);
                            ObjectNode d = ardcVocabRestTemplate.getForObject(dl, ObjectNode.class);

                            if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                JsonNode target = d.get("result").get("primaryTopic");

                                PlatformVocabModel model = PlatformVocabModel
                                        .builder()
                                        .label(label.apply(target))
                                        .definition(definition.apply(target))
                                        .about(about.apply(target))
                                        .build();

                                if(target.has("broadMatch") && !target.get("broadMatch").isEmpty()) {
                                    for(JsonNode bm : target.get("broadMatch")) {
                                        if (!results.containsKey(bm.asText())) {
                                            results.put(bm.asText(), new ArrayList<>());
                                        }
                                        // We will have multiple cat under the same parent
                                        results.get(bm.asText()).add(model);
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
        return results;
    }

    protected PlatformVocabModel buildPlatformVocabModel(JsonNode currentNode, JsonNode outerNode) {
        if (currentNode instanceof ObjectNode objectNode) {
            if (objectNode.has("prefLabel") && objectNode.has("_about")) {
                return PlatformVocabModel.builder()
                        .about(about.apply(currentNode))
                        .label(label.apply(currentNode))
                        .build();
            }
        } else if (currentNode instanceof TextNode textNode) {
            if (textNode.asText().contains("parameter_classes")) {
                return PlatformVocabModel.builder()
                        .about(textNode.asText())
                        .label(this.findLabelByAbout(outerNode, textNode.asText()))
                        .build();
            }
        }
        return null;
    }

    protected String findLabelByAbout(JsonNode node, String c) {
        for (JsonNode item : node.get("items")) {
            if (about.apply(item).contains(c)) {
                return label.apply(item);
            }
        }
        return null;
    }

    public List<PlatformVocabModel> getPlatformVocabs(String vocabApiBase) {
        Map<String, List<PlatformVocabModel>> leaves = getLeafNodeOfParameterVocab(vocabApiBase);
        List<PlatformVocabModel> results = new ArrayList<>();

        String url = String.format(vocabApiBase + platformVocabApiPath);

        while (url != null) {
            try {
                log.debug("Query api -> {}", url);

                ObjectNode r = ardcVocabRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {
                            List<PlatformVocabModel> broader = new ArrayList<>();
                            List<PlatformVocabModel> narrower = new ArrayList<>();


                            log.debug("Processing label {}", label.apply(j));

                            if (j.has("broader")) {
                                for (JsonNode b : j.get("broader")) {
                                    broader.add(this.buildPlatformVocabModel(b, node));
                                }
                            }

                            if (j.has("narrower")) {
                                for (JsonNode b : j.get("narrower")) {
                                    PlatformVocabModel c = this.buildPlatformVocabModel(b, node);
                                    // The record comes from ardc have two levels only, so the second level for sure
                                    // is empty, but the third level info comes form another link (aka the leaves)
                                    // and therefore we can attach it to the second level to for the third.
                                    if(leaves.containsKey(c.getAbout())) {
                                        c.setNarrower(leaves.get(c.getAbout()));
                                    }
                                    narrower.add(c);
                                }
                            }

                            PlatformVocabModel model = PlatformVocabModel
                                    .builder()
                                    .label(label.apply(j))
                                    .definition(definition.apply(j))
                                    .about(about.apply(j))
                                    .broader(broader)
                                    .narrower(narrower)
                                    .build();

                            results.add(model);
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
                log.error("Fail connect {}, vocab return likely outdated", url);
                url = null;
            }
        }

        return results;
    }
}
