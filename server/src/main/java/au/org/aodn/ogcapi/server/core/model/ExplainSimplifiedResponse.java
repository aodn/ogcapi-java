package au.org.aodn.ogcapi.server.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Flattened view of an elastic search explain result, returned by
 * /api/v1/ogc/admin/explain?format=simple
 * Internal debugging/troubleshooting usage only.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExplainSimplifiedResponse {

    private Total total;
    private List<Hit> hits;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Total {
        private Long value;
        private String relation;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "rank", "id", "title", "final_score",
            "es_relevance", "internal_score", "quality_multiplier", "matched_terms"
    })
    public static class Hit {
        private Integer rank;
        private String id;
        private String title;

        @JsonProperty("final_score")
        private Double finalScore;

        /** The _score elastic search produced before the quality multiplier was applied */
        @JsonProperty("es_relevance")
        private Double esRelevance;

        /** The stored summaries.score of the document */
        @JsonProperty("internal_score")
        private Double internalScore;

        /** The normalised internalscore (summaries.score / total) */
        @JsonProperty("quality_multiplier")
        private Double qualityMultiplier;

        /** The per matched field and term and score which contributed to the esRelevance */
        @JsonProperty("matched_terms")
        private List<MatchedTerm> matchedTerms;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MatchedTerm {
        private String field;
        private String term;
        private Double score;
    }
}
