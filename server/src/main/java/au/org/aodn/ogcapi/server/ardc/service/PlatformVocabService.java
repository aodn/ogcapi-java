package au.org.aodn.ogcapi.server.ardc.service;

import au.org.aodn.ogcapi.server.ardc.model.PlatformVocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;


@Slf4j
@Service("PlatformVocabService")
public class PlatformVocabService {
    @Autowired
    protected RestTemplate ardcVocabRestTemplate;

    protected static String platformCategoryVocabApi = "/aodn-platform-category-vocabulary/version-1-2/concept.json";
    protected static String platformVocabApi = "/aodn-platform-vocabulary/version-6-1/concept.json";
    protected static String resourceDetailsApi = "/aodn-platform-vocabulary/version-6-1/resource.json?uri=%s";

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
    protected Map<String, List<PlatformVocabModel>> getPlatformVocabLeafNodes(String vocabApiBase) {
        Map<String, List<PlatformVocabModel>> results = new HashMap<>();
        String url = String.format(vocabApiBase + platformVocabApi);

        while (url != null && !url.isEmpty()) {
            ObjectNode r = ardcVocabRestTemplate.getForObject(url, ObjectNode.class);
            if (r != null && !r.isEmpty()) {
                JsonNode node = r.get("result");

                if (isNodeValid.apply(node, "items")) {
                    for (JsonNode j : node.get("items")) {
                        // Now we need to construct link to detail resources
                        String dl = String.format(vocabApiBase + resourceDetailsApi, about.apply(j));
                        try {
                            log.debug("Query api -> {}", dl);
                            ObjectNode d = ardcVocabRestTemplate.getForObject(dl, ObjectNode.class);

                            if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                JsonNode target = d.get("result").get("primaryTopic");

                                PlatformVocabModel vocab = PlatformVocabModel
                                        .builder()
                                        .label(label.apply(target))
                                        .definition(definition.apply(target))
                                        .about(about.apply(target))
                                        .build();

                                List<PlatformVocabModel> vocabNarrower = new ArrayList<>();
                                if(target.has("narrower") && !target.get("narrower").isEmpty()) {
                                    for(JsonNode narrower : target.get("narrower")) {
                                        if (narrower.has("_about")) {
                                            PlatformVocabModel narrowerNode = buildVocabByResourceUri(about.apply(narrower), vocabApiBase);
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
        }
        return results;
    }

    protected PlatformVocabModel buildVocabByResourceUri(String uri, String vocabApiBase) {
        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, uri);
        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = ardcVocabRestTemplate.getForObject(detailsUrl, ObjectNode.class);
            if(isNodeValid.apply(detailsObj, "result") && isNodeValid.apply(detailsObj.get("result"), "primaryTopic")) {
                JsonNode target = detailsObj.get("result").get("primaryTopic");
                return PlatformVocabModel
                        .builder()
                        .label(label.apply(target))
                        .definition(definition.apply(target))
                        .about(uri)
                        .build();
            }
        } catch(Exception e) {
            log.error("Item not found in resource {}", detailsUrl);
        }
        return null;
    }

    public List<PlatformVocabModel> getPlatformVocabs(String vocabApiBase) {
        Map<String, List<PlatformVocabModel>> leafNodes = getPlatformVocabLeafNodes(vocabApiBase);
        List<PlatformVocabModel> results = new ArrayList<>();

        String url = String.format(vocabApiBase + platformCategoryVocabApi);

        while (url != null && !url.isEmpty()) {
            try {
                ObjectNode r = ardcVocabRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {

                            List<PlatformVocabModel> narrower = new ArrayList<>();
                            if (leafNodes.containsKey(about.apply(j))) {
                                narrower = leafNodes.get(about.apply(j));
                            }

                            PlatformVocabModel vocab = PlatformVocabModel
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
