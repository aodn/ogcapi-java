package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import java.util.*;

@Slf4j
@Service
public class PlatformVocabProcessor extends VocabProcessorBase {
    protected static String platformCategoryVocabPath = "/aodn-platform-category-vocabulary/version-1-2/concept.json";
    protected static String platformVocabPath = "/aodn-platform-vocabulary/version-6-1/concept.json";
    protected static String platformVocabDetailsPath = "/aodn-platform-vocabulary/version-6-1/resource.json?uri=%s";

    private Map<String, List<VocabModel>> getVocabLeafNodes(String vocabApiBase) {
        Map<String, List<VocabModel>> results = new HashMap<>();

        String url = String.format(vocabApiBase + platformVocabPath);

        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);
                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (isNodeValid.apply(node, "items")) {
                        for (JsonNode j : node.get("items")) {
                            // Now we need to construct link to detail resources
                            String dl = String.format(vocabApiBase + platformVocabDetailsPath, about.apply(j));
                            try {
                                log.debug("Query api -> {}", dl);
                                ObjectNode d = vocabRestTemplate.getForObject(dl, ObjectNode.class);

                                if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                    JsonNode target = d.get("result").get("primaryTopic");

                                    VocabModel vocab = VocabModel
                                            .builder()
                                            .label(label.apply(target))
                                            .definition(definition.apply(target))
                                            .about(about.apply(target))
                                            .build();

                                    List<VocabModel> vocabNarrower = new ArrayList<>();
                                    if(target.has("narrower") && !target.get("narrower").isEmpty()) {
                                        for(JsonNode narrower : target.get("narrower")) {
                                            if (narrower.has("_about")) {
                                                VocabModel narrowerNode = buildVocabByResourceUri(about.apply(narrower), vocabApiBase, platformVocabDetailsPath);
                                                if (narrowerNode != null) {
                                                    vocabNarrower.add(narrowerNode);
                                                }
                                            }
                                        }
                                    }
                                    if (!vocabNarrower.isEmpty()) {
                                        vocab.setNarrower(vocabNarrower);
                                    }

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
                log.error("Fail connect {}, vocab return likely outdated", url);
                url = null;
            }
        }
        return results;
    }

    protected List<VocabModel> getPlatformVocabs(String vocabApiBase) {
        Map<String, List<VocabModel>> leafNodes = getVocabLeafNodes(vocabApiBase);
        List<VocabModel> results = new ArrayList<>();

        String url = String.format(vocabApiBase + platformCategoryVocabPath);

        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {

                            log.debug("Processing label {}", label.apply(j));

                            List<VocabModel> narrower = new ArrayList<>();
                            if (leafNodes.containsKey(about.apply(j))) {
                                narrower = leafNodes.get(about.apply(j));
                            }

                            VocabModel vocab = VocabModel
                                    .builder()
                                    .label(label.apply(j))
                                    .definition(definition.apply(j))
                                    .about(about.apply(j))
                                    .narrower(narrower)
                                    .build();

                            results.add(vocab);
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
