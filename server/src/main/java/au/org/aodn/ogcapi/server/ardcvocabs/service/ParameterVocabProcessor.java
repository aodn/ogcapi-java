package au.org.aodn.ogcapi.server.ardcvocabs.service;

import au.org.aodn.ogcapi.server.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.*;

@Slf4j
@Component
public class ParameterVocabProcessor extends VocabProcessorBase {
    protected static String parameterCategoryVocabPath = "/aodn-parameter-category-vocabulary/version-2-1/concept.json";
    protected static String parameterVocabPath = "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json";
    protected static String parameterVocabDetailsPath = "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=%s";

    private VocabModel buildVocabModel(JsonNode currentNode, JsonNode outerNode) {
        if (currentNode instanceof ObjectNode objectNode) {
            if (objectNode.has("prefLabel") && objectNode.has("_about")) {
                return VocabModel.builder()
                        .about(about.apply(currentNode))
                        .label(label.apply(currentNode).toLowerCase())
                        .build();
            }
        } else if (currentNode instanceof TextNode textNode) {
            if (textNode.asText().contains("parameter_classes")) {
                return VocabModel.builder()
                        .about(textNode.asText())
                        .label(Objects.requireNonNull(findLabelByAbout(outerNode, textNode.asText())).toLowerCase())
                        .build();
            }
        }
        return null;
    }

    private String findLabelByAbout(JsonNode node, String c) {
        for (JsonNode item : node.get("items")) {
            if (about.apply(item).contains(c)) {
                return label.apply(item);
            }
        }
        return null;
    }

    private Map<String, List<VocabModel>> getVocabLeafNodes(String vocabApiBase) {
        Map<String, List<VocabModel>> results = new HashMap<>();

        String url = String.format(vocabApiBase + parameterVocabPath);

        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);
                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (isNodeValid.apply(node, "items")) {
                        for (JsonNode j : node.get("items")) {
                            // Now we need to construct link to detail resources
                            String dl = String.format(vocabApiBase + parameterVocabDetailsPath, about.apply(j));
                            try {
                                log.debug("Query api -> {}", dl);
                                ObjectNode d = vocabRestTemplate.getForObject(dl, ObjectNode.class);

                                if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                    JsonNode target = d.get("result").get("primaryTopic");

                                    VocabModel vocab = VocabModel
                                            .builder()
                                            .label(label.apply(target).toLowerCase())
                                            .definition(definition.apply(target))
                                            .about(about.apply(target))
                                            .build();

                                    if (target.has("broadMatch") && !target.get("broadMatch").isEmpty()) {
                                        for(JsonNode bm : target.get("broadMatch")) {
                                            results.computeIfAbsent(bm.asText(), k -> new ArrayList<>()).add(vocab);
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
            } catch (RestClientException e) {
                log.error("Fail connect {}, parameter vocab return likely outdated", url);
                url = null;
            }
        }
        return results;
    }

    protected List<VocabModel> getParameterVocabs(String vocabApiBase) {
        Map<String, List<VocabModel>> leafNodes = getVocabLeafNodes(vocabApiBase);
        List<VocabModel> results = new ArrayList<>();

        String url = String.format(vocabApiBase + parameterCategoryVocabPath);

        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {
                            log.debug("Processing label {}", label.apply(j));

                            List<VocabModel> broader = new ArrayList<>();
                            List<VocabModel> narrower = new ArrayList<>();

                            if (j.has("broader")) {
                                for (JsonNode b : j.get("broader")) {
                                    broader.add(buildVocabModel(b, node));
                                }
                            }

                            if (j.has("narrower")) {
                                for (JsonNode b : j.get("narrower")) {
                                    VocabModel c = buildVocabModel(b, node);
                                    // The record comes from ardc have two levels only, so the second level for sure
                                    // is empty, but the third level info comes form another link (aka the leaves)
                                    // and therefore we can attach it to the second level to for the third.
                                    if(c != null && leafNodes.containsKey(c.getAbout())) {
                                        c.setNarrower(leafNodes.get(c.getAbout()));
                                    }
                                    narrower.add(c);
                                }
                            }

                            VocabModel model = VocabModel
                                    .builder()
                                    .label(label.apply(j).toLowerCase())
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
                log.error("Fail connect {}, parameter vocab return likely outdated", url);
                url = null;
            }
        }

        return results;
    }
}
