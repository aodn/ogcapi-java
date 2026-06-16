package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.BaseTestClass;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the wiring the unit test can't reach: against a real Elasticsearch, the service
 * discovers the synonyms set from the index's own settings and resolves a typed acronym to its full
 * name(s). Nothing about the dictionary is configured here — it is read from the index.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AcronymSuggestionServiceIT extends BaseTestClass {

    private static final String IT_INDEX = "acronym-it";
    private static final String SYNONYM_SET = "portal-acronyms-it";

    private AcronymSuggestionService service;

    @BeforeAll
    public void setUp() throws IOException {
        // 1. the synonyms set es-indexer would maintain
        client.synonyms().putSynonym(s -> s
                .id(SYNONYM_SET)
                .synonymsSet(List.of(
                        SynonymRule.of(r -> r.synonyms("aad => australian antarctic division")),
                        SynonymRule.of(r -> r.synonyms("aadc => australian antarctic data centre")))));

        // 2. an index whose acronym filter points at that set (mirrors portal_records_index_schema.json)
        String indexBody = """
                {
                  "settings": { "analysis": {
                    "analyzer": { "acronym_search_analyser": {
                      "type": "custom", "tokenizer": "standard", "filter": ["lowercase", "acronym_synonym_filter"] } },
                    "filter": { "acronym_synonym_filter": {
                      "type": "synonym_graph", "synonyms_set": "%s", "updateable": true } } } },
                  "mappings": { "properties": {
                    "title": { "type": "text", "fields": { "synonyms": {
                      "type": "text", "search_analyzer": "acronym_search_analyser" } } } } }
                }""".formatted(SYNONYM_SET);
        client.indices().create(c -> c.index(IT_INDEX).withJson(new StringReader(indexBody)));

        service = new AcronymSuggestionService(client, transport, new ObjectMapper(), IT_INDEX);
    }

    @AfterAll
    public void tearDown() throws IOException {
        client.indices().delete(d -> d.index(IT_INDEX));
        client.synonyms().deleteSynonym(d -> d.id(SYNONYM_SET));
    }

    /** End-to-end: set name auto-discovered from the index, rules fetched, parsed and matched by prefix. */
    @Test
    void resolvesAcronymPrefixToFullNamesViaTheIndexSynonymsSet() {
        List<String> suggestions = service.suggestFullNames("aad");

        assertEquals(2, suggestions.size());
        assertTrue(suggestions.contains("Australian Antarctic Division"));
        assertTrue(suggestions.contains("Australian Antarctic Data Centre"));
    }
}
