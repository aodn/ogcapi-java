package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.features.model.PointGeoJSON;
import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureProperty;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)         // We need to use @BeforeAll @AfterAll with not static method
public class RestApiTest extends BaseTestClass {

    @Value("${elasticsearch.index.pageSize:2000}")
    protected Integer pageSize;

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
        super.createElasticIndex();
    }

    @Test
    @Order(1)
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }

    @Test
    public void verifyCorrectPageSizeAndScoreWithQuery2() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

        logger.debug("Start verifyCorrectPageSizeAndScoreWithQuery");

        // Given 6 records and we set page to 4, that means each query elastic return 4 record only
        // and the logic to load the reset can kick in.
        super.insertJsonToElasticRecordIndex(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        // Call rest api directly and get query result with search on "dataset"
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections?q=dataset&filter=page_size=1 AND score>=1.3",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        collections.getBody().getCollections().forEach(i -> logger.info("uuid {}, {}", i.getId(), collections.getBody().getSearchAfter()));
        assertFalse(true);
    }
}
