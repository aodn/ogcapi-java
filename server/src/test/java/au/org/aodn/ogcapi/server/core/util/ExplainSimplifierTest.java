package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.ExplainSimplifiedResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExplainSimplifierTest {

    /**
     * The sort values decide the rank ahead of the score, e.g. the dataset_group priority
     * sits first, so the simplified hit has to report them as plain values.
     */
    @Test
    public void simplifiedHitReportsTheSortValuesOfTheHit() {
        Hit<ObjectNode> hit = Hit.of(h -> h
                .index("records")
                .id("7709f541-fc0c-4318-b5b9-9053aa474e0e")
                .score(2.5)
                .sort(FieldValue.of(1),
                        FieldValue.of(2.5),
                        FieldValue.of("7709f541-fc0c-4318-b5b9-9053aa474e0e")));

        ExplainSimplifiedResponse.Hit simplified = ExplainSimplifier.toSimplifiedHit(hit, 1);

        assertEquals(
                List.of(1L, 2.5, "7709f541-fc0c-4318-b5b9-9053aa474e0e"),
                simplified.getSortValues());
    }

    @Test
    public void sortValuesAreLeftOutWhenTheHitCarriesNone() {
        Hit<ObjectNode> hit = Hit.of(h -> h
                .index("records")
                .id("7709f541-fc0c-4318-b5b9-9053aa474e0e")
                .score(2.5));

        ExplainSimplifiedResponse.Hit simplified = ExplainSimplifier.toSimplifiedHit(hit, 1);

        assertNull(simplified.getSortValues());
    }
}
