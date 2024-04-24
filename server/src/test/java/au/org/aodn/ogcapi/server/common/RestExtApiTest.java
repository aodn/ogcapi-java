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
     * The search is a fuzzy search based on title and description. So you expect 1 hit only
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
        assertTrue(Objects.requireNonNull(response.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("MRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
    }

    /**
     * The search is a fuzzy search based on title and description. So you expect 1 hit only
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
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=australia", String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("MRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
    }

    /**
     * The search is a fuzzy search based on title and description. So you expect 1 hit only
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnTypoInputNoCategoriesFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("IMOS - Zooplankton Abundance and Biomass Index (CPR)"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("MRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
    }

    @Test
    public void verifyApiResponseOnTypoInputSingleCategoriesFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(discovery_categories='wave')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("Ocean acidification historical reconstruction"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("IMOS - Zooplankton Abundance and Biomass Index (CPR)"));
    }

    @Test
    public void verifyApiResponseOnTypoInputMultipleCategoriesFilter() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(discovery_categories='temperature' AND discovery_categories='chlorophyll')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).isEmpty());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("IMOS - Zooplankton Abundance and Biomass Index (CPR)")); //
        assertFalse(Objects.requireNonNull(response.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("Ocean acidification historical reconstruction"));
    }

    @Test
    public void verifyApiResponseOnTypoInputMultipleCategoriesFilterNoResults() throws IOException {
        super.insertJsonToElasticIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=imos&filter=(discovery_categories='cat1' AND discovery_categories='cat2')", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(Objects.requireNonNull(response.getBody()).contains("IMOS - Zooplankton Abundance and Biomass Index (CPR)")); //
        assertFalse(Objects.requireNonNull(response.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("Ocean acidification historical reconstruction"));
    }

    @Test
    public void verifyApiResponseOnCategorySuggestions() throws IOException {
        super.insertTestAodnDiscoveryCategories();

        super.insertJsonToElasticIndex(
            "19da2ce7-138f-4427-89de-a50c724f5f54.json"
        );


        ResponseEntity<String> wavResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=wav", String.class);
        assertTrue(wavResponse.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(wavResponse.getBody()).contains("category_suggestions"));
        assertTrue(Objects.requireNonNull(wavResponse.getBody()).contains("record_suggestions"));
        assertTrue(Objects.requireNonNull(wavResponse.getBody()).contains("IMOS - ACORN - South Australia Gulfs HF ocean radar site (South Australia, Australia) - Delayed mode wave"));
        assertTrue(wavResponse.getBody().contains("\"category_suggestions\":[\"Wave\"]"));

        ResponseEntity<String> tempResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=temp", String.class);
        assertTrue(Objects.requireNonNull(tempResponse.getBody()).contains("category_suggestions"));
        assertTrue(Objects.requireNonNull(tempResponse.getBody()).contains("record_suggestions"));
        assertTrue(tempResponse.getBody().contains("\"category_suggestions\":[\"Temperature\"]"));


        ResponseEntity<String> preResponse = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=pre", String.class);
        assertTrue(Objects.requireNonNull(preResponse.getBody()).contains("category_suggestions"));
        assertTrue(Objects.requireNonNull(preResponse.getBody()).contains("record_suggestions"));
        assertTrue(preResponse.getBody().contains("Water pressure"));
        assertTrue(preResponse.getBody().contains("Air pressure"));
    }
}
