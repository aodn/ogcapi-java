package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.ExplainSimplifiedResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExplainSimplifierTest {

    private static final String UUID = "7709f541-fc0c-4318-b5b9-9053aa474e0e";

    private static Hit<ObjectNode> hitSortedBy(FieldValue... sort) {
        return Hit.of(h -> h
                .index("records")
                .id(UUID)
                .score(2.5)
                .sort(List.of(sort)));
    }

    /** The sort of the request that ranks a text search: dataset_group priority, _score, uuid */
    private static List<SortOptions> textSearchSort() {
        return List.of(
                SortOptions.of(so -> so.script(s -> s
                        .type(ScriptSortType.Number)
                        .script(sc -> sc
                                .lang("painless")
                                .source("if (!doc.containsKey('summaries.dataset_group')) { return 0; } return 1;"))
                        .order(SortOrder.Desc))),
                SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))),
                SortOptions.of(so -> so.field(f -> f.field("id.keyword").order(SortOrder.Asc))));
    }

    /**
     * The sort values decide the rank ahead of the score, e.g. the dataset_group priority
     * sits first, so the simplified hit reports them, each named by its sort key. A script
     * sort carries no name, the field its source reads names it.
     */
    @Test
    public void simplifiedHitNamesEverySortValueByItsSortKey() {
        Hit<ObjectNode> hit = hitSortedBy(FieldValue.of(1), FieldValue.of(2.5), FieldValue.of(UUID));

        ExplainSimplifiedResponse.Hit simplified =
                ExplainSimplifier.toSimplifiedHit(hit, 1, textSearchSort());

        assertEquals(
                List.of(
                        sortValue("summaries.dataset_group", 1L),
                        sortValue("_score", 2.5),
                        sortValue("id.keyword", UUID)),
                simplified.getSortValues());
    }

    @Test
    public void sortValuesAreKeptUnnamedWhenTheSortOptionsDoNotLineUp() {
        Hit<ObjectNode> hit = hitSortedBy(FieldValue.of(2.5), FieldValue.of(UUID));

        // one option for two values, so no pairing is safe
        ExplainSimplifiedResponse.Hit simplified = ExplainSimplifier.toSimplifiedHit(
                hit, 1,
                List.of(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc)))));

        assertEquals(
                List.of(sortValue(null, 2.5), sortValue(null, UUID)),
                simplified.getSortValues());
    }

    @Test
    public void sortValuesAreLeftOutWhenTheHitCarriesNone() {
        Hit<ObjectNode> hit = Hit.of(h -> h
                .index("records")
                .id(UUID)
                .score(2.5));

        ExplainSimplifiedResponse.Hit simplified =
                ExplainSimplifier.toSimplifiedHit(hit, 1, textSearchSort());

        assertNull(simplified.getSortValues());
    }

    private static ExplainSimplifiedResponse.SortValue sortValue(String field, Object value) {
        return ExplainSimplifiedResponse.SortValue.builder()
                .field(field)
                .value(value)
                .build();
    }
}
