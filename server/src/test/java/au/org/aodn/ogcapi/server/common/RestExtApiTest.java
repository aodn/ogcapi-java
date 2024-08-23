package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.BaseTestClass;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)         // We need to use @BeforeAll @AfterAll with not static method
public class RestExtApiTest extends BaseTestClass {

    @BeforeAll
    public void beforeClass() throws IOException {
        super.createElasticIndex();
    }

    @AfterAll
    public void clear() throws IOException {
        super.clearElasticIndex();
    }

    @BeforeEach
    public void afterTest() throws IOException {
        super.clearElasticIndex();
    }

    @Test
    @Order(1)
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnIncompleteInput() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        // complete input
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=austr", String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("australia research development"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("australian bight"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("australian plankton survey auscpr"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("hf radar coverage area"));
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnCompleteInput() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        // complete input
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=australian", String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("australian bight"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("australian plankton survey auscpr"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("australia research development"));
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnTypoInputNoParameterVocabsFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("sa node imos"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("collections imos auscpr zooplankton"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("imos auscpr zooplankton"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("dataset comprises zooplankton"));
    }

    @Test
    public void verifyApiResponseOnTypoInputSingleParameterVocabsFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(parameter_vocabs='wave')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());

        assertTrue(Objects.requireNonNull(response.getBody()).contains("sa node imos"));

        // these have "imos" but are filtered out by filter=
        assertFalse(Objects.requireNonNull(response.getBody()).contains("collections imos auscpr zooplankton"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("imos auscpr zooplankton"));
    }

    @Test
    public void verifyApiResponseOnTypoInputMultipleParameterVocabsFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(parameter_vocabs='temperature' AND parameter_vocabs='chlorophyll')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());

        assertTrue(Objects.requireNonNull(response.getBody()).contains("collections imos auscpr zooplankton"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("imos auscpr zooplankton"));

        // this has "imos" but is filtered out by filter=
        assertFalse(Objects.requireNonNull(response.getBody()).contains("sa node imos"));
    }

    @Test
    public void verifyApiResponseOnTypoInputMultipleParameterVocabsFilterNoResults() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(parameter_vocabs='cat1' AND parameter_vocabs='cat2')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).contains("collections imos auscpr zooplankton"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("imos auscpr zooplankton"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("sa node imos"));
    }

    @Test
    public void verifyApiResponseOnParameterVocabSuggestions() throws IOException {
        super.insertTestAodnDiscoveryParameterVocabs();

        super.insertJsonToElasticIndex(
            "19da2ce7-138f-4427-89de-a50c724f5f54.json"
        );


        ResponseEntity<String> wavResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=wav", String.class);
        assertTrue(wavResponse.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(wavResponse.getBody()).contains("parameter_vocab_suggestions"));
        assertTrue(Objects.requireNonNull(wavResponse.getBody()).contains("record_suggestions"));
        assertTrue(wavResponse.getBody().contains("\"parameter_vocab_suggestions\":[\"Wave\"]"));

        ResponseEntity<String> tempResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=temp", String.class);
        assertTrue(Objects.requireNonNull(tempResponse.getBody()).contains("parameter_vocab_suggestions"));
        assertTrue(Objects.requireNonNull(tempResponse.getBody()).contains("record_suggestions"));
        assertTrue(tempResponse.getBody().contains("\"parameter_vocab_suggestions\":[\"Temperature\"]"));


        ResponseEntity<String> preResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=pre", String.class);
        assertTrue(Objects.requireNonNull(preResponse.getBody()).contains("parameter_vocab_suggestions"));
        assertTrue(Objects.requireNonNull(preResponse.getBody()).contains("record_suggestions"));
        assertTrue(preResponse.getBody().contains("Water pressure"));
        assertTrue(preResponse.getBody().contains("Air pressure"));
    }
}
