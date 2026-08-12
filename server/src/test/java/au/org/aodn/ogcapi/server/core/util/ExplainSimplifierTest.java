package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.ExplainSimplifiedResponse;
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    public void semanticTextDetailsAreAggregatedWithoutExposingSparseFeatures() {
        ExplanationDetail semantic = semanticScoreDetail(12.833507F);
        List<ExplanationDetail> scored = List.of(
                detail("summaries.dataset_group:(imos imos wave measurements)^100.0", 100.0F),
                semantic,
                detail("ConstantScore(spatial query)", 1.0F));

        assertEquals(12.833507, ExplainSimplifier.semanticScoreOf(scored), 0.000001);

        List<ExplainSimplifiedResponse.MatchedTerm> terms = new ArrayList<>();
        List<ExplainSimplifiedResponse.MatchedFilter> filters = new ArrayList<>();
        ExplainSimplifier.collectScoreParts(scored, terms, filters);

        assertEquals(2, filters.size());
        assertFalse(filters.stream()
                .anyMatch(filter -> filter.getDescription().contains("embeddings field")));
    }

    @Test
    public void simplifiedHitSeparatesSemanticFromOtherRelevance() {
        ExplanationDetail score = ExplanationDetail.of(d -> d
                .description("_score: ")
                .value(113.833504F)
                .details(
                        detail("summaries.dataset_group:(imos imos wave measurements)^100.0", 100.0F),
                        semanticScoreDetail(12.833507F),
                        detail("ConstantScore(spatial query)", 1.0F)));

        ObjectNode source = MAPPER.createObjectNode();
        source.put("title", "Wave buoys Observations");
        source.putObject("summaries").put("score", 144.0);

        Hit<ObjectNode> hit = Hit.of(h -> h
                .index("records")
                .id("wave-record")
                .score(112.27414)
                .source(source)
                .explanation(e -> e
                        .description("sum of:")
                        .value(112.27414F)
                        .details(score)));

        ExplainSimplifiedResponse.Hit simplified = ExplainSimplifier.toSimplifiedHit(hit, 1);

        assertNotNull(simplified.getSemanticScore());
        assertEquals(12.833507, simplified.getSemanticScore(), 0.000001);
        assertEquals(101.0, simplified.getNonSemanticScore(), 0.00001);
        assertEquals(12.833507 / 113.833504, simplified.getSemanticContribute(), 0.000001);
        assertEquals(2, simplified.getFilters().size());
    }

    private ExplanationDetail semanticScoreDetail(float score) {
        return ExplanationDetail.of(d -> d
                .description("Score based on 2 child docs in range from 25 to 85, using score mode Max")
                .value(score)
                .details(child -> child
                        .description("sum of:")
                        .value(score)
                        .details(feature -> feature
                                .description("Linear function on the "
                                        + "description_semantic.inference.chunks.embeddings field "
                                        + "for the wave feature, computed as w * S from:")
                                .value(score)
                                .details(weight -> weight
                                        .description("w, weight of this function")
                                        .value(2.578F)))));
    }

    private ExplanationDetail detail(String description, float score) {
        return ExplanationDetail.of(d -> d.description(description).value(score));
    }
}
