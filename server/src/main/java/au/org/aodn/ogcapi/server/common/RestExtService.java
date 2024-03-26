package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.model.CategoryVocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Use to store the logic and make it easier to test
 */
@Slf4j
@Service("CommonRestExtService")
public class RestExtService {
    @Autowired
    protected RestTemplate template;

    protected static String path = "/version-2-1/concept.json";

    public List<CategoryVocabModel> getParameterCategory(String vocabApiBase) {
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
                            Map<String, String> broader = new HashMap<>();
                            Map<String, String> narrower = new HashMap<>();

                            log.debug("Processing label {}", j.get("prefLabel").get("_value").asText());
                            if (j.has("broader")) {
                                for (JsonNode b : j.get("broader")) {
                                    if (b.has("prefLabel") && b.has("_about")) {
                                        broader.put(b.get("prefLabel").get("_value").asText(), b.get("_about").asText());
                                    }
                                }
                            }

                            if (j.has("narrower")) {
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
