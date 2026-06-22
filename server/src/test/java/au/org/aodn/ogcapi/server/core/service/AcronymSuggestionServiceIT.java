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
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Against a real Elasticsearch, the service reads its acronym dictionary from the index's own synonyms set
 * and resolves a typed acronym to its full name. Built from the real schema; nothing configured in code.
 * <p>Example: input "nrmn" -> output ["National Reef Monitoring Network"].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AcronymSuggestionServiceIT extends BaseTestClass {

    private static final String IT_INDEX = "acronym-it";
    private static final String SYNONYM_SET = "portal-acronyms-it";
    private static final String RECORD_SCHEMA = "/schema/portal_records_index_schema.json";
    // the schema leaves the set name as this placeholder: es-indexer fills it in prod, the test does it below
    private static final String SYNONYM_SET_PLACEHOLDER = "${portal-acronyms}";

    private AcronymSuggestionService service;

    @BeforeAll
    public void setUp() throws IOException {
        publishAcronymSynonyms();
        createRecordsIndexFromRealSchema();
        service = new AcronymSuggestionService(client, transport, new ObjectMapper(), IT_INDEX);
    }

    @AfterAll
    public void tearDown() throws IOException {
        client.indices().delete(request -> request.index(IT_INDEX));
        client.synonyms().deleteSynonym(request -> request.id(SYNONYM_SET));
    }

    @Test
    void suggestsFullNameForTypedAcronym() {
        // input (typed acronym) -> output (full name suggestions)
        // "nrmn" -> ["National Reef Monitoring Network"]
        assertEquals(List.of("National Reef Monitoring Network"), service.suggestFullNames("nrmn"));
        // "soop" -> ["Ship Of Opportunity"]
        assertEquals(List.of("Ship Of Opportunity"), service.suggestFullNames("soop"));
    }

    // --- helpers that build the index the service reads from ---

    /**
     * Publish the acronym -> full-name rules, as es-indexer would in production.
     * Rules are stored lowercase; the service title-cases them for display
     * (e.g. "national reef monitoring network" -> "National Reef Monitoring Network").
     */
    private void publishAcronymSynonyms() throws IOException {
        client.synonyms().putSynonym(request -> request
                .id(SYNONYM_SET)
                .synonymsSet(List.of(
                        SynonymRule.of(rule -> rule.synonyms("nrmn => national reef monitoring network")),
                        SynonymRule.of(rule -> rule.synonyms("soop => ship of opportunity")))));
    }

    /** Create the records index from the real schema, pointed at the synonyms set above. */
    private void createRecordsIndexFromRealSchema() throws IOException {
        String indexBody = readRecordSchema().replace(SYNONYM_SET_PLACEHOLDER, SYNONYM_SET);
        client.indices().create(request -> request.index(IT_INDEX).withJson(new StringReader(indexBody)));
    }

    /** Read the real records schema from the classpath (it ships in the stacmodel jar). */
    private static String readRecordSchema() throws IOException {
        try (InputStream stream = AcronymSuggestionServiceIT.class.getResourceAsStream(RECORD_SCHEMA)) {
            if (stream == null) {
                throw new IOException("Schema not found on classpath: " + RECORD_SCHEMA);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
