package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExplainSimplifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A free text search of "csiro temperature", the dataset group clause holds the whole input
     * alongside each of its words. Elastic search renders it as one run of words,
     * "summaries.dataset_group:(csiro csiro temperature temperature)^100.0", which the request
     * is needed to separate again.
     */
    private Map<String, String> datasetGroupRequest() throws JsonProcessingException {
        JsonNode request = MAPPER.readTree("""
                {"query":{"bool":{"should":[
                  {"match":{"title":{"query":"csiro temperature"}}},
                  {"terms":{"summaries.dataset_group":["csiro temperature","csiro","temperature"],
                            "boost":100.0}}]}}}""");

        return ExplainSimplifier.termsClausesOf(request);
    }

    @Test
    public void separateTermsValuesCommaSeparatesTheValuesOfTheClause() throws JsonProcessingException {
        assertEquals(
                "summaries.dataset_group:(csiro,csiro temperature,temperature)^100.0",
                ExplainSimplifier.separateTermsValues(
                        "summaries.dataset_group:(csiro csiro temperature temperature)^100.0",
                        datasetGroupRequest()));
    }
}
