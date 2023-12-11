package au.org.aodn.ogcapi.server.core.common;

import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.core.BaseTestClass;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)         // We need to use @BeforeAll @AfterAll with not static method
public class RestApiIT extends BaseTestClass {

    @AfterAll
    public void afterClass() {
        super.shutdownElasticSearch();
    }

    @BeforeAll
    public void beforeClass() throws IOException {
        super.createElasticIndex();
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
     * @throws IOException
     */
    @Test
    public void verifyApiCollectionsQueryOnText() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=reproduction", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "Only 1 hit");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");

        // This time we make a typo but we should still get the result back as Fuzzy search
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=temperatura", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "Only 1 hit");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 7709f541-fc0c-4318-b5b9-9053aa474e0e");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=temperatura,reproduction", Collections.class);
        assertEquals(2, collections.getBody().getCollections().size(), "hit 2");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 7709f541-fc0c-4318-b5b9-9053aa474e0e");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");
    }
    /**
     * The datetime field after xxx/.. xxx/ etc
     */
    @Test
    public void verifyDateTimeAfterBounds() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1994-02-16T13:00:00Z/..", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1 because 1 document do not have start date");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");

        // The syntax slightly diff but they are the same / = /.. the time is slightly off with one of the record so still get 1
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T15:10:44Z/", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1 because 1 document do not have start date");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");

        // The syntax slightly diff but they are the same / = /.. the time is slightly off with one of the record so still get 1
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T14:10:44Z/", Collections.class);
        assertEquals(2, collections.getBody().getCollections().size(), "hit 2");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 7709f541-fc0c-4318-b5b9-9053aa474e0e");
    }
    /**
     * The datetime field before ../xxx or /xxx etc
     */
    @Test
    public void verifyDateTimeBeforeBounds() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(3, collections.getBody().getCollections().size(), "hit all");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/2007-06-05T14:00:00Z", Collections.class);
        assertEquals(2, collections.getBody().getCollections().size(), "hit 2 only given this time");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");
    }
    /**
     * The datetime field before xxx/yyy
     */
    @Test
    public void verifyDateTimeBetweenBounds() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T14:10:44Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(2, collections.getBody().getCollections().size(), "hit 2, one record no start date");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");

        // The time is slight off by 1 sec so only 1 document returned
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T14:10:45Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");
    }
    /**
     * One of the record in the dataset contains two start/end date in the temporal field.
     * @throws IOException
     */
    @Test
    public void verifyDateTimeBoundsWithDiscreteTime() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a.json"     // This one have two start/end
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=2006-02-28T13:00:00Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        // The start datetime is 1 sec more then one of the start date in the record, however it still fit into the next start/end slot, so should return same result
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1991-12-31T13:00:01Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        // Now we check the before which should include the same record as it match one of the start/end time
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/1995-03-30T13:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(3, collections.getBody().getCollections().size(), "hit 3, this record have 2 start/end");
    }
    /**
     * The properties param control what properties should be return to improve the speed of data transfer between component.
     */
    @Test
    public void verifyPropertiesParameter() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2007-06-05T14:00:00Z&properties=id,title", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, only one record");

        assertNotNull(collections.getBody().getCollections().get(0).getId());
        assertNotNull(collections.getBody().getCollections().get(0).getTitle());
        assertNull(collections.getBody().getCollections().get(0).getDescription());

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2007-06-05T14:00:00Z&properties=id,title,description", Collections.class);
        assertNotNull(collections.getBody().getCollections().get(0).getDescription());
    }
    /**
     * Check Common Query Language behavior
     * @throws IOException
     */
    @Test
    public void verifyCQLPropertyIsNullIsNotNull() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",    // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider not null
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, only one record");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NOT NULL", Collections.class);
        assertEquals(1, collections.getBody().getCollections().size(), "hit 1, only one record");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");
    }
}
