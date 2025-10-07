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
    }

    @Test
    @Order(1)
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }
    /**
     * We want to test the pageableSearch inside the elastic search is right or wrong by setting up more than 4 canned data, then
     * query all to get them back even the search result return from elastic is break down into 4 + 2
     */
    @Test
    public void verifyCorrectInternalPagingLargeData() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

        // Given 6 records and we set page to 4, that means each query elastic return 4 record only
        // and the logic to load the reset can kick in.
        super.insertJsonToElasticRecordIndex(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        // Call rest api directly and get query result
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        assertEquals(6, Objects.requireNonNull(collections.getBody()).getCollections().size(), "Total equals");
        assertEquals(6, collections.getBody().getTotal(), "Get total works");

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
    /**
     * with page_size set, the max number of record return will equals page_size
     */
    @Test
    public void verifyCorrectPageSizeDataReturn() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

        // Given 6 records and we set page to 4, that means each query elastic return 4 record only
        // and the logic to load the reset can kick in.
        super.insertJsonToElasticRecordIndex(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        // Call rest api directly and get query result
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections?filter=page_size=3",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        // Given request page size is 3, only 3 return this time
        assertEquals(3,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );
        // Total number of record should be this
        assertEquals(6, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals("1.0", collections.getBody().getSearchAfter().get(0), "Search after 1 value");
        assertEquals(
                "100",
                collections.getBody().getSearchAfter().get(1),
                "search_after 2 arg"
        );
        assertEquals(
                "str:bf287dfe-9ce4-4969-9c59-51c39ea4d011",
                collections.getBody().getSearchAfter().get(2),
                "search_after 3 arg"
        );

        // Now make sure all id exist
        Set<String> ids = new HashSet<>(List.of(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                "19da2ce7-138f-4427-89de-a50c724f5f54",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011"
        ));

        for(Collection collection : Objects.requireNonNull(collections.getBody()).getCollections()) {
            assertTrue(ids.contains(collection.getId()),"Contains " + collection.getId());
        }

        // Now if we provided the search after we should get the next batch
        collections = testRestTemplate.exchange(
                getBasePath() + "/collections?filter=page_size=3 AND search_after=" +
                        String.format("'%s||%s||%s'",
                                collections.getBody().getSearchAfter().get(0),
                                collections.getBody().getSearchAfter().get(1),
                                collections.getBody().getSearchAfter().get(2)),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        // Given request page size is 3, only 3 return this time
        assertEquals(3,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );

        ids = new HashSet<>(List.of(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                "bc55eff4-7596-3565-e044-00144fdd4fa6",
                "516811d7-cd1e-207a-e0440003ba8c79dd"
        ));

        for(Collection collection : Objects.requireNonNull(collections.getBody()).getCollections()) {
            assertTrue(ids.contains(collection.getId()),"Contains in next batch " + collection.getId());
        }
    }
    /**
     * Extreme case, page size set to 1 and query text "dataset" and page one by one. Only part of the json
     * will be return, the sort value should give you the next item and you will be able to go to next one.
     * The first sort value is the relevant and because of query text the value will be something greater than 1.0
     */
    @Test
    public void verifyCorrectPageSizeDataReturnWithQuery() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

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
                getBasePath() + "/collections?q=dataset&filter=page_size=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        // Given request page size is 1
        assertEquals(1,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );
        // Total number of record should be this
        assertEquals(5, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals(
                "str:bc55eff4-7596-3565-e044-00144fdd4fa6",
                collections.getBody().getSearchAfter().get(2),
                "search_after 2 arg"
        );

        // Now the same search, same page but search_after the result above given sort value
        // intended to give space after comma for negative test
        collections = testRestTemplate.exchange(
                getBasePath() + "/collections?q=dataset&filter=page_size=1 AND search_after=" +
                        String.format("'%s||%s||%s'",
                                collections.getBody().getSearchAfter().get(0),
                                collections.getBody().getSearchAfter().get(1),
                                "bc55eff4-7596-3565-e044-00144fdd4fa6"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        assertEquals(1,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );
        // Total number of record should be this as the same search criteria applies
        assertEquals(5, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals(
                "str:7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getSearchAfter().get(2),
                "search_after 3 arg"
        );

        // Now the same search, diff page but search_after the result above given sort value
        // set a bigger page size which exceed more than record hit as negative test
        collections = testRestTemplate.exchange(
                getBasePath() + "/collections?q=dataset&filter=page_size=3 AND search_after=" +
                        String.format("'%s||%s ||%s'",
                                collections.getBody().getSearchAfter().get(0),
                                collections.getBody().getSearchAfter().get(1),
                                "5c418118-2581-4936-b6fd-d6bedfe74f62"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        assertEquals(3,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct, total hit is 4, we move to the third record"
        );
        // Total number of record should be this as the same search criteria applies
        assertEquals(5, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals(
                "str:19da2ce7-138f-4427-89de-a50c724f5f54",
                collections.getBody().getSearchAfter().get(2),
                "search_after 3 value"
        );
    }
    /**
     * Similar to verifyCorrectPageSizeDataReturnWithQuery and add score in the query,
     * this is used to verify a bug fix where page_size and score crash the query
     */
    @Test
    public void verifyCorrectPageSizeAndScoreWithQuery() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

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
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        // Given request page size is 1
        assertEquals(1,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );
        // Total number of record should be this
        assertEquals(5, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals(
                "80",
                collections.getBody().getSearchAfter().get(1),
                "search_after 2 value"
        );
        assertEquals(
                "str:7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getSearchAfter().get(2),
                "search_after 3 value"
        );

        // Now the same search, same page but search_after the result above given sort value
        // intended to give space after comma for negative test
        collections = testRestTemplate.exchange(
                getBasePath() + "/collections?q=dataset&filter=page_size=6 AND score>=1.3 AND search_after=" +
                        String.format("'%s|| %s || %s'",
                                collections.getBody().getSearchAfter().get(0),
                                collections.getBody().getSearchAfter().get(1),
                                "bc55eff4-7596-3565-e044-00144fdd4fa6"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, collections.getStatusCode(), "Get status OK");
        assertEquals(4,
                Objects.requireNonNull(collections.getBody()).getCollections().size(),
                "Record return size correct"
        );
        // Total number of record should be this as the same search criteria applies
        assertEquals(5, collections.getBody().getTotal(), "Get total works");

        // The search after give you the value to go to next batch
        assertEquals(3, collections.getBody().getSearchAfter().size(), "search_after three fields");
        assertEquals(
                "str:5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getSearchAfter().get(2),
                "Search after 2 value"
        );
    }

    @Test
    public void verifyGetSingleCollection() throws IOException {
        super.insertJsonToElasticRecordIndex(
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
        super.insertJsonToElasticRecordIndex(
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
    /**
     * Verify the function correctly sum up the values for feature id summary
     * @throws IOException - Not expect to throw
     */
    @Disabled("Skipping this test temporarily")
    @Test
    public void verifyAggregationFeatureSummaryCorrect() throws IOException {
        super.insertJsonToElasticCODataIndex(
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.1.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.2.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample2.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.1.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.2.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<FeatureCollectionGeoJSON> collection = testRestTemplate.getForEntity(
                getBasePath() + "/collections/35234913-aa3c-48ec-b9a4-77f822f66ef8/items/summary",
                FeatureCollectionGeoJSON.class);

        assertNotNull(collection.getBody(), "Body not null");

        FeatureCollectionGeoJSON json = collection.getBody();
        assertEquals(3, json.getFeatures().size(), "Features correct");

        // Sort make sure compare always same order
        List<FeatureGeoJSON> sf = json.getFeatures().stream()
                        .sorted((a,b) -> b.getGeometry().hashCode() - a.getGeometry().hashCode())
                .toList();
        // Sample1
        FeatureGeoJSON featureGeoJSON1 = new FeatureGeoJSON();
        featureGeoJSON1.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        featureGeoJSON1.setGeometry(new PointGeoJSON()
                .type(PointGeoJSON.TypeEnum.POINT)
                .coordinates(List.of(BigDecimal.valueOf(159.26), BigDecimal.valueOf(-24.72)))
        );
        featureGeoJSON1.setProperties(Map.of(
                FeatureProperty.COUNT.getValue(), 42.0,
                FeatureProperty.START_TIME.getValue(), "2023-02-01T00:00:00.000Z",
                FeatureProperty.END_TIME.getValue(), "2023-02-01T00:00:00.000Z"

        ));
        assertEquals(featureGeoJSON1, sf.get(0), "featureGeoJSON1");

        // Sample3
        FeatureGeoJSON featureGeoJSON2 = new FeatureGeoJSON();
        featureGeoJSON2.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        featureGeoJSON2.setGeometry(new PointGeoJSON()
                .type(PointGeoJSON.TypeEnum.POINT)
                .coordinates(List.of(BigDecimal.valueOf(154.81), BigDecimal.valueOf(-26.2)))
        );
        featureGeoJSON2.setProperties(Map.of(
                FeatureProperty.COUNT.getValue(), 48.0,
                FeatureProperty.START_TIME.getValue(), "2023-02-01T00:00:00.000Z",
                FeatureProperty.END_TIME.getValue(), "2024-03-01T00:00:00.000Z"

        ));
        assertEquals(featureGeoJSON2, sf.get(1), "featureGeoJSON2");

        FeatureGeoJSON featureGeoJSON3 = new FeatureGeoJSON();
        featureGeoJSON3.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        featureGeoJSON3.setGeometry(new PointGeoJSON()
                .type(PointGeoJSON.TypeEnum.POINT)
                .coordinates(List.of(BigDecimal.valueOf(153.56), BigDecimal.valueOf(-26.59)))
        );
        featureGeoJSON3.setProperties(Map.of(
                FeatureProperty.COUNT.getValue(), 14.0,
                FeatureProperty.START_TIME.getValue(), "2023-02-01T00:00:00.000Z",
                FeatureProperty.END_TIME.getValue(), "2023-02-01T00:00:00.000Z"

        ));
        assertEquals(featureGeoJSON3, sf.get(2), "featureGeoJSON3");
    }
    /**
     * We add more sample data and will trigger page load.
     * @throws IOException - Not expect to throw
     */
    @Disabled("Skipping this test temporarily")
    @Test
    public void verifyAggregationFeatureSummaryWithPageCorrect() throws IOException {
        assertEquals(4, pageSize, "This test only works with small page");

        super.insertJsonToElasticCODataIndex(
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.1.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample1.2.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample2.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.1.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample3.2.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample4.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample5.0.json",
                "cloudoptimized/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample5.1.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<FeatureCollectionGeoJSON> collection = testRestTemplate.getForEntity(
                getBasePath() + "/collections/35234913-aa3c-48ec-b9a4-77f822f66ef8/items/summary",
                FeatureCollectionGeoJSON.class);

        assertNotNull(collection.getBody(), "Body not null");

        FeatureCollectionGeoJSON json = collection.getBody();
        assertEquals(5, json.getFeatures().size(), "Features correct");

        // Sort make sure compare always same order
        List<FeatureGeoJSON> sf = json.getFeatures().stream()
                .sorted((a,b) -> b.getGeometry().hashCode() - a.getGeometry().hashCode())
                .toList();

        // Sample1
        FeatureGeoJSON featureGeoJSON1 = new FeatureGeoJSON();
        featureGeoJSON1.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        featureGeoJSON1.setGeometry(new PointGeoJSON()
                .type(PointGeoJSON.TypeEnum.POINT)
                .coordinates(List.of(BigDecimal.valueOf(163.56), BigDecimal.valueOf(-26.59)))
        );
        featureGeoJSON1.setProperties(Map.of(
                FeatureProperty.COUNT.getValue(), 14.0,
                FeatureProperty.START_TIME.getValue(), "2023-02-01T00:00:00.000Z",
                FeatureProperty.END_TIME.getValue(), "2023-02-01T00:00:00.000Z"

        ));
        assertEquals(featureGeoJSON1, sf.get(0), "featureGeoJSON1");

        // Sample5
        FeatureGeoJSON featureGeoJSON2 = new FeatureGeoJSON();
        featureGeoJSON2.setType(FeatureGeoJSON.TypeEnum.FEATURE);
        featureGeoJSON2.setGeometry(new PointGeoJSON()
                .type(PointGeoJSON.TypeEnum.POINT)
                .coordinates(List.of(BigDecimal.valueOf(163.56), BigDecimal.valueOf(-126.59)))
        );
        featureGeoJSON2.setProperties(Map.of(
                FeatureProperty.COUNT.getValue(), 20.0,
                FeatureProperty.START_TIME.getValue(), "2022-12-01T00:00:00.000Z",
                FeatureProperty.END_TIME.getValue(), "2023-02-01T00:00:00.000Z"

        ));
        assertEquals(featureGeoJSON2, sf.get(1), "featureGeoJSON2");
    }
}
