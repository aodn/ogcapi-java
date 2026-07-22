package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.ExplainSimplifiedResponse;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacBasicField;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.Explanation;
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format the nested explain response into the simplified structure exposed by
 * /api/v1/ogc/admin/explain?format=simple
 */
public class ExplainSimplifier {
    /**
     * A leaf scoring node is "weight(<query> in <docId>) [<similarity>], result of:",
     * the doc id is numeric which keeps the split unambiguous for a quoted phrase.
     */
    protected static final Pattern WEIGHT_PATTERN = Pattern.compile("^weight\\((.*) in \\d+\\)");

    protected static final String WEIGHT_PREFIX = "weight(";

    protected static final String SYNONYM_PREFIX = "Synonym(";

    protected static final String RELEVANCE_DESCRIPTION_PREFIX = "_score:";

    protected static final Comparator<ExplainSimplifiedResponse.MatchedTerm> BY_SCORE_DESC =
            Comparator.comparing(
                    ExplainSimplifiedResponse.MatchedTerm::getScore,
                    Comparator.reverseOrder());

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

        List<ExplainSimplifiedResponse.MatchedTerm> collected = new ArrayList<>();
        collectTerms(explanation.details(), collected);

        collected.sort(BY_SCORE_DESC);
        return collected;
    }

    protected static void collectTerms(List<ExplanationDetail> details,
                                       List<ExplainSimplifiedResponse.MatchedTerm> collected) {
        if (details == null) {
            return;
        }

        for (ExplanationDetail detail : details) {
            String description = detail.description();

            // most nodes are idf/tf breakdowns, skip the regex for them
            if (description == null || !description.startsWith(WEIGHT_PREFIX)) {
                collectTerms(detail.details(), collected);
                continue;
            }

            Matcher matcher = WEIGHT_PATTERN.matcher(description);

            if (matcher.find()) {
                ExplainSimplifiedResponse.MatchedTerm term = toMatchedTerm(matcher.group(1), detail.value());

                if (term != null) {
                    // the same field and term can score in more than one should clause, keep every one
                    collected.add(term);
                }
                // the children only break the term score down into idf/tf, nothing more to collect
                continue;
            }

            collectTerms(detail.details(), collected);
        }
    }

    /**
     * Interpret the query text of a leaf scoring node, it is one of
     *   title:network                                     a term match, exact or fuzzy
     *   title:"ocean temperature"                         a phrase match
     *   Synonym(title.synonyms:soop title.synonyms:ships) an acronym expansion
     * A synonym group carries one score for the whole group so it stays one entry.
     */
    protected static ExplainSimplifiedResponse.MatchedTerm toMatchedTerm(String query, float score) {
        String body = query;

        if (body.startsWith(SYNONYM_PREFIX) && body.endsWith(")")) {
            body = body.substring(SYNONYM_PREFIX.length(), body.length() - 1);
        }

        int separator = body.indexOf(':');
        if (separator <= 0 || separator == body.length() - 1) {
            // not a field:term form, e.g. a range or a function query, nothing to report
            return null;
        }

        String field = body.substring(0, separator);
        // a synonym group repeats "field:" before every alternative, keep the terms only
        String term = body.substring(separator + 1)
                .replace(field + ":", "")
                .replace("\"", "")
                .trim();

        return ExplainSimplifiedResponse.MatchedTerm.builder()
                .field(field)
                .term(term)
                .score((double) score)
                .build();
    }

    protected static String stringField(ObjectNode source, String path) {
        JsonNode node = valueAt(source, path);
        return node.isTextual() ? node.asText() : null;
    }

    protected static Double doubleField(ObjectNode source, String path) {
        JsonNode node = valueAt(source, path);
        return node.isNumber() ? node.asDouble() : null;
    }

    /**
     * Resolve a dotted field name such as "summaries.score" against the returned _source,
     * a missing node is returned when the field was not fetched
     */
    protected static JsonNode valueAt(ObjectNode source, String path) {
        return source == null
                ? MissingNode.getInstance()
                : source.at("/" + path.replace('.', '/'));
    }
}
