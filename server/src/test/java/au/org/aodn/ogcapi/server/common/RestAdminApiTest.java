package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.BaseTestClass;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RestAdminApiTest extends BaseTestClass {

    @BeforeAll
    public void beforeClass() {
        super.createElasticIndex();
    }

    @AfterAll
    public void clear() {
        super.clearElasticIndex();
    }

    @BeforeEach
    public void afterTest() {
        super.clearElasticIndex();
    }

    @Test
    public void explainEndpointMatchesNormalSearchOrdering() throws IOException {
        insertRecordsWithExplicitIds(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        URI normalUri = UriComponentsBuilder
                .fromUriString(getBasePath() + "/collections")
                .queryParam("q", "dataset")
                .queryParam("filter", "page_size=3")
                .build()
                .encode()
                .toUri();
        URI explainUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("q", "dataset")
                .queryParam("filter", "page_size=3")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> normalResponse = testRestTemplate.getForEntity(normalUri, JsonNode.class);
        ResponseEntity<JsonNode> explainResponse = testRestTemplate.getForEntity(explainUri, JsonNode.class);

        assertTrue(normalResponse.getStatusCode().is2xxSuccessful());
        assertTrue(explainResponse.getStatusCode().is2xxSuccessful());

        JsonNode normalBody = Objects.requireNonNull(normalResponse.getBody());
        JsonNode explainBody = Objects.requireNonNull(explainResponse.getBody());
        List<String> normalIds = StreamSupport.stream(normalBody.path("collections").spliterator(), false)
                .map(collection -> collection.path("id").asText())
                .toList();
        List<String> explainIds = StreamSupport.stream(explainBody.path("hits").spliterator(), false)
                .map(hit -> hit.path("id").asText())
                .toList();

        assertEquals(3, explainBody.path("request").path("size").asInt());
        assertTrue(explainBody.path("request").path("query").has("script_score"));
        assertTrue(explainBody.path("request").has("_source"));
        assertFalse(explainBody.path("request").path("_source").asBoolean());
        assertEquals("eq", explainBody.path("total").path("relation").asText());
        assertEquals(normalIds, explainIds);
        assertTrue(StreamSupport.stream(explainBody.path("hits").spliterator(), false)
                .allMatch(hit -> hit.path("explanation").has("description")));
    }

    @Test
    public void explainEndpointAppliesCqlFilter() throws IOException {
        insertRecordsWithExplicitIds(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json");

        URI explainUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("filter", "parameter_vocabs='temperature'")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(explainUri, JsonNode.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertEquals(1, body.path("total").path("value").asLong());
        assertEquals("7709f541-fc0c-4318-b5b9-9053aa474e0e", body.path("hits").path(0).path("id").asText());
        assertTrue(body.path("request").path("query").path("bool").path("filter").isArray());
        assertTrue(body.path("hits").path(0).path("explanation").has("details"));
    }

    @Test
    public void explainEndpointReturnsSimplifiedFormat() throws IOException {
        insertRecordsWithExplicitIds(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        URI fullUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("q", "dataset")
                .queryParam("filter", "page_size=3")
                .build()
                .encode()
                .toUri();
        URI simpleUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("q", "dataset")
                .queryParam("filter", "page_size=3")
                .queryParam("format", "simple")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> fullResponse = testRestTemplate.getForEntity(fullUri, JsonNode.class);
        ResponseEntity<JsonNode> simpleResponse = testRestTemplate.getForEntity(simpleUri, JsonNode.class);

        assertTrue(fullResponse.getStatusCode().is2xxSuccessful());
        assertTrue(simpleResponse.getStatusCode().is2xxSuccessful());
        JsonNode fullBody = Objects.requireNonNull(fullResponse.getBody());
        JsonNode simpleBody = Objects.requireNonNull(simpleResponse.getBody());

        // the simplified payload drops the elastic search request and the explanation tree
        assertFalse(simpleBody.has("request"));
        assertTrue(StreamSupport.stream(simpleBody.path("hits").spliterator(), false)
                .noneMatch(hit -> hit.has("explanation")));

        assertEquals("eq", simpleBody.path("total").path("relation").asText());
        assertEquals(fullBody.path("total").path("value").asLong(), simpleBody.path("total").path("value").asLong());

        // both formats run the same query, so the hits and their order must agree
        List<String> fullIds = StreamSupport.stream(fullBody.path("hits").spliterator(), false)
                .map(hit -> hit.path("id").asText())
                .toList();
        List<String> simpleIds = StreamSupport.stream(simpleBody.path("hits").spliterator(), false)
                .map(hit -> hit.path("id").asText())
                .toList();
        assertEquals(fullIds, simpleIds);

        JsonNode top = simpleBody.path("hits").path(0);
        assertEquals(1, top.path("rank").asInt());
        assertFalse(top.path("title").asText().isBlank());
        assertTrue(top.path("internal_score").isNumber());
        assertEquals(
                top.path("final_score").asDouble(),
                top.path("es_relevance").asDouble() * top.path("quality_multiplier").asDouble(),
                0.0001);

        JsonNode matchedTerms = top.path("matched_terms");
        assertTrue(matchedTerms.isArray());
        assertFalse(matchedTerms.isEmpty());

        double previous = Double.MAX_VALUE;
        for (JsonNode matchedTerm : matchedTerms) {
            assertFalse(matchedTerm.path("field").asText().isBlank());
            assertFalse(matchedTerm.path("term").asText().isBlank());
            assertTrue(matchedTerm.path("score").asDouble() <= previous, "matched_terms must be sorted by score");
            previous = matchedTerm.path("score").asDouble();
        }
    }

    @Test
    public void explainSimplifiedFormatReportsMultiWordMatches() throws IOException {
        // "Ocean acidification historical reconstruction", elastic search decomposes the
        // multi word clauses into one scoring leaf per term and per field
        insertRecordsWithExplicitIds("7709f541-fc0c-4318-b5b9-9053aa474e0e.json");

        URI simpleUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("q", "ocean acidification")
                .queryParam("format", "simple")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(simpleUri, JsonNode.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode terms = Objects.requireNonNull(response.getBody())
                .path("hits").path(0).path("matched_terms");

        assertFalse(terms.isEmpty());
        for (JsonNode term : terms) {
            assertFalse(term.path("field").asText().isBlank());
            assertFalse(term.path("term").asText().isBlank());
            assertFalse(term.path("match_type").asText().isBlank());
        }

        // both words of the query are reported, and separately for each field they hit,
        // rather than being collapsed into one entry per field
        List<String> reported = StreamSupport.stream(terms.spliterator(), false)
                .map(term -> term.path("field").asText() + ":" + term.path("term").asText())
                .toList();
        assertTrue(reported.contains("title:ocean"));
        assertTrue(reported.contains("title:acidification"));
        assertTrue(reported.size() > 2, "a multi word query hits more than one field");
    }

    @Test
    public void explainEndpointFallsBackToFullForUnknownFormat() throws IOException {
        insertRecordsWithExplicitIds("7709f541-fc0c-4318-b5b9-9053aa474e0e.json");

        URI explainUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain")
                .queryParam("q", "temperature")
                .queryParam("format", "unknown")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(explainUri, JsonNode.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).has("request"));
    }

    @Test
    public void explainUuidEndpointUsesDocumentId() throws IOException {
        String uuid = "7709f541-fc0c-4318-b5b9-9053aa474e0e";
        insertRecordsWithExplicitIds(uuid + ".json");

        URI explainUri = UriComponentsBuilder
                .fromUriString(getAdminBasePath() + "/explain/" + uuid)
                .queryParam("q", "temperature")
                .build()
                .encode()
                .toUri();

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(explainUri, JsonNode.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertTrue(body.path("response").path("matched").asBoolean());
        assertTrue(body.path("response").path("explanation").has("description"));
    }

    private String getAdminBasePath() {
        return getBasePath() + "/admin";
    }

    private void insertRecordsWithExplicitIds(String... filenames) throws IOException {
        for (String filename : filenames) {
            File file = ResourceUtils.getFile("classpath:databag/" + filename);
            String id = filename.substring(0, filename.length() - ".json".length());

            try (Reader reader = new FileReader(file)) {
                client.index(i -> i
                        .index(record_index_name)
                        .id(id)
                        .withJson(reader));
            }
        }
        client.indices().refresh();
    }

}
