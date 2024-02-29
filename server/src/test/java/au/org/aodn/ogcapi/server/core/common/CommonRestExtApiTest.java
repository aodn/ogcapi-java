package au.org.aodn.ogcapi.server.core.common;

import au.org.aodn.ogcapi.server.core.BaseTestClass;

import org.junit.jupiter.api.*;
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
public class CommonRestExtApiTest extends BaseTestClass {

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
                "51e7f42c-e01d-47ea-b8e4-f702e85b5948.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json"
        );

        // incomplete input
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=aus", String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("GMRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
        assertTrue(Objects.requireNonNull(response.getBody()).contains("50m Multibeam Dataset of Australia - Tile number: SD57"));
    }

    /**
     * The search is a fuzzy search based on title and description. So you expect 1 hit only
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnCompleteInput() throws IOException {
        super.insertJsonToElasticIndex(
                "51e7f42c-e01d-47ea-b8e4-f702e85b5948.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json"
        );

        // incomplete input
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=australia", String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("50m Multibeam Dataset of Australia - Tile number: SD57"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("MRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
    }

    /**
     * The search is a fuzzy search based on title and description. So you expect 1 hit only
     *
     * @throws IOException
     */
    @Test
    public void verifyApiResponseOnTypoInput() throws IOException {
        super.insertJsonToElasticIndex(
                "51e7f42c-e01d-47ea-b8e4-f702e85b5948.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json"
        );

        // incomplete input
        ResponseEntity<String> response = testRestTemplate.getForEntity(getExternalBasePath() + "/autocomplete?input=austalia", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(response.getBody()).contains("50m Multibeam Dataset of Australia - Tile number: SD57"));
        assertFalse(Objects.requireNonNull(response.getBody()).contains("MRT AusSeabed PL019 Workshop-1: Attribute Values and Definitions"));
    }
}