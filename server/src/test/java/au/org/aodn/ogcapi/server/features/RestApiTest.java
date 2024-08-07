package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.BaseTestClass;
import io.swagger.models.Method;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
     * We want to test the pageableSearch function is right or wrong by setting up more than 4 canned data, then
     * query all to get them back
     */
    @Test
    public void verifyCorrectPagingLargeData() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

        // Given 6 records and we set page to 4, that means each query elastic return 4 record only
        // and the logic to load the reset can kick in.
        super.insertJsonToElasticIndex(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        // Call rest api directly and get query result
        ResponseEntity<Collections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(collections.getStatusCode(), HttpStatus.OK, "Get status OK");
        assertEquals(Objects.requireNonNull(collections.getBody()).getCollections().size(), 6, "Total equals");

        // Now make sure all id exist
        Set<String> ids = new HashSet<>(List.of(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                "19da2ce7-138f-4427-89de-a50c724f5f54",
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                "bc55eff4-7596-3565-e044-00144fdd4fa6",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011"
        ));

        for(Collection collection : Objects.requireNonNull(collections.getBody()).getCollections()) {
            assertTrue(ids.contains(collection.getId()),"Contains " + collection.getId());
        }
    }

    @Test
    public void verifyGetSingleCollection() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<Collection> collection = testRestTemplate.getForEntity(
                getBasePath() + "/collections/516811d7-cd1e-207a-e0440003ba8c79dd",
                Collection.class);

        assertNotNull(collection.getBody(), "Body not null");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collection.getBody().getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");
    }

    @Test
    public void verifyBBoxCorrect() throws IOException {
        super.insertJsonToElasticIndex(
                "ae86e2f5-eaaf-459e-a405-e654d85adb9c.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<Collection> collection = testRestTemplate.getForEntity(
                getBasePath() + "/collections/ae86e2f5-eaaf-459e-a405-e654d85adb9c",
                Collection.class);

        assertNotNull(collection.getBody(), "Body not null");

        List<List<BigDecimal>> bbox = collection.getBody().getExtent().getSpatial().getBbox();
        assertEquals(
                24,
                bbox.size(),
                "Count of bbox");

        // Should be something like this but order may be diff
        // "bbox" : [
        // [ 113.0, -43.0, 154.0, -9.0 ], [ 115.0, -21.0, 117.0, -19.0 ], [ 114.0, -21.0, 115.0, -20.0 ],
        // [ 152.0, -22.0, 153.0, -21.0 ], [ 113.0, -22.0, 114.0, -21.0 ], [ 151.0, -24.0, 153.0, -22.0 ],
        // [ 130.0, -10.0, 131.0, -9.0 ], [ 121.0, -17.0, 122.0, -15.0 ], [ 130.0, -13.0, 131.0, -12.0 ],
        // [ 127.0, -14.0, 129.0, -9.0 ], [ 145.0, -15.0, 146.0, -14.0 ], [ 123.0, -15.0, 124.0, -14.0 ],
        // [ 119.0, -18.0, 120.0, -17.0 ], [ 147.0, -20.0, 148.0, -18.0 ], [ 153.0, -28.0, 154.0, -27.0 ],
        // [ 153.0, -31.0, 154.0, -30.0 ], [ 137.0, -34.0, 138.0, -33.0 ], [ 114.0, -33.0, 116.0, -31.0 ],
        // [ 121.0, -34.0, 122.0, -33.0 ], [ 151.0, -35.0, 152.0, -33.0 ], [ 150.0, -37.0, 151.0, -36.0 ],
        // [ 134.0, -37.0, 137.0, -34.0 ], [ 141.0, -39.0, 142.0, -38.0 ], [ 148.0, -43.0, 149.0, -42.0 ] ],
        Optional<List<BigDecimal>> target = bbox.stream()
                .filter(box -> box.get(0).doubleValue() == 141.0)
                .filter(box -> box.get(1).doubleValue() == -39.0)
                .filter(box -> box.get(2).doubleValue() == 142.0)
                .filter(box -> box.get(3).doubleValue() == -38.0)
                .findFirst();

        assertTrue(target.isPresent(), "Target bbox found 1");

        target = bbox.stream()
                .filter(box -> box.get(0).doubleValue() == 152.0)
                .filter(box -> box.get(1).doubleValue() == -22.0)
                .filter(box -> box.get(2).doubleValue() == 153.0)
                .filter(box -> box.get(3).doubleValue() == -21.0)
                .findFirst();

        assertTrue(target.isPresent(), "Target bbox found 2");

        logger.info(bbox.get(0).toString());
        // The first is the overall bounding box
        assertEquals(113.0, bbox.get(0).get(0).doubleValue(), "Overall bounding box coor 1");
        assertEquals(-43.0, bbox.get(0).get(1).doubleValue(), "Overall bounding box coor 2");
        assertEquals(154.0, bbox.get(0).get(2).doubleValue(), "Overall bounding box coor 3");
        assertEquals(-9.0, bbox.get(0).get(3).doubleValue(), "Overall bounding box coor 4");
    }
}
