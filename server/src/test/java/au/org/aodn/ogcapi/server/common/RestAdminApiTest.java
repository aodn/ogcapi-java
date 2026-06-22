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
