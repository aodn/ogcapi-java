package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.ExplainSimplifiedResponse;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacBasicField;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.Explanation;
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format the nested explain response into the simplified structure exposed by
 * /api/v1/ogc/admin/explain?format=simple
 */
public class ExplainSimplifier {
    /**
     * A leaf scoring node looks like
     * "weight(title:network in 123) [PerFieldSimilarity], result of:"
     */
    protected static final Pattern TERM_PATTERN =
            Pattern.compile("^weight\\(([^:()\\s]+):([^\\s()]+) in \\S+\\)");

    protected static final String RELEVANCE_DESCRIPTION_PREFIX = "_score:";

    private ExplainSimplifier() {
    }

    public static ExplainSimplifiedResponse from(SearchResponse<ObjectNode> response) {
        ExplainSimplifiedResponse.Total total = null;

        if (response.hits().total() != null) {
            total = ExplainSimplifiedResponse.Total.builder()
                    .value(response.hits().total().value())
                    .relation(response.hits().total().relation().jsonValue())
                    .build();
        }

        List<ExplainSimplifiedResponse.Hit> hits = new ArrayList<>();
        int rank = 1;

        for (Hit<ObjectNode> hit : response.hits().hits()) {
            hits.add(toSimplifiedHit(hit, rank++));
        }

        return ExplainSimplifiedResponse.builder()
                .total(total)
                .hits(hits)
                .build();
    }

    protected static ExplainSimplifiedResponse.Hit toSimplifiedHit(Hit<ObjectNode> hit, int rank) {
        Explanation explanation = hit.explanation();

        // hit.score() keeps the precision elastic search reported, the explanation values are floats
        Double finalScore = hit.score();
        Double esRelevance = explanation != null ? (double) relevanceOf(explanation) : null;

        Double qualityMultiplier = null;
        if (finalScore != null && esRelevance != null && esRelevance != 0.0) {
            qualityMultiplier = finalScore / esRelevance;
        }

        return ExplainSimplifiedResponse.Hit.builder()
                .rank(rank)
                .id(hit.id())
                .title(stringField(hit.source(), StacBasicField.Title.searchField))
                .matched(true)
                .finalScore(finalScore)
                .esRelevance(esRelevance)
                .internalScore(doubleField(hit.source(), StacSummeries.Score.searchField))
                .qualityMultiplier(qualityMultiplier)
                .matchedTerms(matchedTermsOf(explanation))
                .build();
    }

    /**
     * For a keyword search the root is the script_score function and the untouched relevance sits
     * in its "_score: " child. Without the script_score wrapper the root value is the relevance.
     */
    protected static float relevanceOf(Explanation explanation) {
        if (explanation.details() != null) {
            for (ExplanationDetail detail : explanation.details()) {
                if (detail.description() != null
                        && detail.description().startsWith(RELEVANCE_DESCRIPTION_PREFIX)) {
                    return detail.value();
                }
            }
        }
        return explanation.value();
    }

    protected static List<ExplainSimplifiedResponse.MatchedTerm> matchedTermsOf(Explanation explanation) {
        if (explanation == null) {
            return List.of();
        }

        Map<String, ExplainSimplifiedResponse.MatchedTerm> collected = new LinkedHashMap<>();
        collectTerms(explanation.details(), collected);

        return collected.values()
                .stream()
                .sorted(Comparator.comparing(
                        ExplainSimplifiedResponse.MatchedTerm::getScore,
                        Comparator.reverseOrder()))
                .toList();
    }

    protected static void collectTerms(List<ExplanationDetail> details,
                                       Map<String, ExplainSimplifiedResponse.MatchedTerm> collected) {
        if (details == null) {
            return;
        }

        for (ExplanationDetail detail : details) {
            Matcher matcher = TERM_PATTERN.matcher(
                    detail.description() == null ? "" : detail.description());

            if (matcher.find()) {
                String field = matcher.group(1);
                String term = matcher.group(2);
                String key = field + ":" + term;

                // the same term can appear more than once, e.g. under a boosted clause, keep the best
                ExplainSimplifiedResponse.MatchedTerm existing = collected.get(key);
                if (existing == null || existing.getScore() < detail.value()) {
                    collected.put(key, ExplainSimplifiedResponse.MatchedTerm.builder()
                            .field(field)
                            .term(term)
                            .score((double) detail.value())
                            .build());
                }
                // the children only break the term score down into idf/tf, nothing more to collect
                continue;
            }

            collectTerms(detail.details(), collected);
        }
    }

    protected static String stringField(ObjectNode source, String path) {
        JsonNode node = valueAt(source, path);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    protected static Double doubleField(ObjectNode source, String path) {
        JsonNode node = valueAt(source, path);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    /**
     * Resolve a dotted path such as "summaries.score" against the returned _source
     */
    protected static JsonNode valueAt(ObjectNode source, String path) {
        if (source == null) {
            return null;
        }

        JsonNode current = source;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current.isMissingNode() ? null : current;
    }
}
