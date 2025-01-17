package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.BaseTestClass;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;


import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)         // We need to use @BeforeAll @AfterAll with not static method
public class RestExtApiTest extends BaseTestClass {

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
    @Order(1)
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }

    ObjectMapper objectMapper = new ObjectMapper();

    /**
     *
     * @throws IOException - not expect to throw
     */
    @Test
    public void verifyApiResponseOnIncompleteInput() throws IOException {
        super.insertJsonToElasticRecordIndex(
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
     * @throws IOException - not expect to throw
     */
    @Test
    public void verifyApiResponseOnCompleteInput() throws IOException {
        super.insertJsonToElasticRecordIndex(
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
     * @throws IOException - not expect to throw
     */
    @Test
    public void verifyApiResponseOnTypoInputNoParameterVocabsFilter() throws IOException {
        super.insertJsonToElasticRecordIndex(
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
        super.insertJsonToElasticRecordIndex(
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
        super.insertJsonToElasticRecordIndex(
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
        super.insertJsonToElasticRecordIndex(
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
        super.insertTestVocabs();

        super.insertJsonToElasticRecordIndex(
            "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );


        ResponseEntity<String> wavResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=wav", String.class);
        assertTrue(wavResponse.getStatusCode().is2xxSuccessful());

        // Parse the JSON string into a JsonNode
        JsonNode suggestedParameterVocabs = objectMapper.readTree(wavResponse.getBody()).path("suggested_parameter_vocabs");
        assertEquals(List.of("wave"), objectMapper.convertValue(suggestedParameterVocabs, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> tempResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=temp", String.class);
        JsonNode suggestedParameterVocabs2 = objectMapper.readTree(tempResponse.getBody()).path("suggested_parameter_vocabs");
        assertEquals(List.of("temperature"), objectMapper.convertValue(suggestedParameterVocabs2, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> preResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=pre", String.class);
        JsonNode suggestedParameterVocabs3 = objectMapper.readTree(preResponse.getBody()).path("suggested_parameter_vocabs");
        // there are no records belong to below parameters, therefore, don't suggest them
        assertNotEquals(Arrays.asList("water pressure", "air pressure"), objectMapper.convertValue(suggestedParameterVocabs3, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }

    @Test
    public void verifyApiResponseOnPlatformVocabSuggestions() throws IOException {
        super.insertTestVocabs();

        super.insertJsonToElasticRecordIndex(
                "record_with_parameter_platform_organisation_vocabs.json"
        );


        ResponseEntity<String> response1 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=bea", String.class);
        assertTrue(response1.getStatusCode().is2xxSuccessful());

        // Parse the JSON string into a JsonNode
        JsonNode suggestedVocabs = objectMapper.readTree(response1.getBody()).path("suggested_platform_vocabs");
        assertEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> response2 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=wera", String.class);
        JsonNode suggestedVocabs2 = objectMapper.readTree(response2.getBody()).path("suggested_platform_vocabs");
        assertEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs2, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> response3 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=vess", String.class);
        JsonNode suggestedVocabs3 = objectMapper.readTree(response3.getBody()).path("suggested_platform_vocabs");
        // there are no records belong to below platform vocab, therefore, don't suggest them
        assertNotEquals(List.of("vessel"), objectMapper.convertValue(suggestedVocabs3, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter
        ResponseEntity<String> response4 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=bea&filter=(platform_vocabs='wera beam forming hf radar')", String.class);
        JsonNode suggestedVocabs4 = objectMapper.readTree(response4.getBody()).path("suggested_platform_vocabs");
        assertEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs4, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // not matching filter
        ResponseEntity<String> response5 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=bea&filter=(platform_vocabs='vessel')", String.class);
        JsonNode suggestedVocabs5 = objectMapper.readTree(response5.getBody()).path("suggested_platform_vocabs");
        assertNotEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs5, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter but not matching input
        ResponseEntity<String> response6 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=random&filter=(platform_vocabs='wera beam forming hf radar')", String.class);
        JsonNode suggestedVocabs6 = objectMapper.readTree(response6.getBody()).path("suggested_platform_vocabs");
        assertNotEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs6, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter and matching input
        ResponseEntity<String> response7 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=bea&filter=(platform_vocabs='wera beam forming hf radar')", String.class);
        JsonNode suggestedVocabs7 = objectMapper.readTree(response7.getBody()).path("suggested_platform_vocabs");
        assertEquals(List.of("wera beam forming hf radar"), objectMapper.convertValue(suggestedVocabs7, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }

    @Test
    public void verifyApiResponseOnOrganisationVocabSuggestions() throws IOException {
        super.insertTestVocabs();

        super.insertJsonToElasticRecordIndex(
                "record_with_parameter_platform_organisation_vocabs.json"
        );


        ResponseEntity<String> response1 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos", String.class);
        assertTrue(response1.getStatusCode().is2xxSuccessful());

        // Parse the JSON string into a JsonNode
        JsonNode suggestedVocabs = objectMapper.readTree(response1.getBody()).path("suggested_organisation_vocabs");
        assertEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> response2 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=ocean", String.class);
        JsonNode suggestedVocabs2 = objectMapper.readTree(response2.getBody()).path("suggested_organisation_vocabs");
        assertEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs2, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        ResponseEntity<String> response3 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=tasm", String.class);
        JsonNode suggestedVocabs3 = objectMapper.readTree(response3.getBody()).path("suggested_organisation_vocabs");
        // there are no records belong to below organisation vocab, therefore, don't suggest them
        assertNotEquals(List.of("university of tasmania"), objectMapper.convertValue(suggestedVocabs3, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter
        ResponseEntity<String> response4 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=ocean&filter=(organisation_vocabs='ocean radar facility, integrated marine observing system (imos)')", String.class);
        JsonNode suggestedVocabs4 = objectMapper.readTree(response4.getBody()).path("suggested_organisation_vocabs");
        assertEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs4, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // not matching filter
        ResponseEntity<String> response5 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=ocean&filter=(organisation_vocabs='university of tasmania')", String.class);
        JsonNode suggestedVocabs5 = objectMapper.readTree(response5.getBody()).path("suggested_organisation_vocabs");
        assertNotEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs5, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter but not matching input
        ResponseEntity<String> response6 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=random&filter=(organisation_vocabs='ocean radar facility, integrated marine observing system (imos)')", String.class);
        JsonNode suggestedVocabs6 = objectMapper.readTree(response6.getBody()).path("suggested_organisation_vocabs");
        assertNotEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs6, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));

        // matching filter and matching input
        ResponseEntity<String> response7 = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=ocean&filter=(organisation_vocabs='ocean radar facility, integrated marine observing system (imos)')", String.class);
        JsonNode suggestedVocabs7 = objectMapper.readTree(response7.getBody()).path("suggested_organisation_vocabs");
        assertEquals(List.of("ocean radar facility, integrated marine observing system (imos)"), objectMapper.convertValue(suggestedVocabs7, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
    }
}
