package au.org.aodn.ogcapi.server.ardcvocabs.service;

import au.org.aodn.ogcapi.server.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public class VocabProcessorBase {
    protected RestTemplate vocabRestTemplate;
    @Autowired
    public final void setVocabRestTemplate(RestTemplate vocabRestTemplate) {
        this.vocabRestTemplate = vocabRestTemplate;
    }

    protected Function<JsonNode, String> label = (node) -> node.get("prefLabel").get("_value").asText();
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;
    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    protected VocabModel buildVocabByResourceUri(String vocabUri, String vocabApiBase, String resourceDetailsApi) {
        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, vocabUri);
        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = vocabRestTemplate.getForObject(detailsUrl, ObjectNode.class);
            if(isNodeValid.apply(detailsObj, "result") && isNodeValid.apply(detailsObj.get("result"), "primaryTopic")) {
                JsonNode target = detailsObj.get("result").get("primaryTopic");
                return VocabModel
                        .builder()
                        .label(label.apply(target))
                        .definition(definition.apply(target))
                        .about(vocabUri)
                        .build();
            }
        } catch(Exception e) {
            log.error("Item not found in resource {}", detailsUrl);
        }
        return null;
    }
}
