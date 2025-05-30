package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;
import au.org.aodn.ogcapi.server.core.model.ExtendedCollections;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)         // We need to use @BeforeAll @AfterAll with not static method
public class RestApiTest extends BaseTestClass {

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
    public void verifyApiGet() {
        RestApi api = new RestApi();
        ResponseEntity<Void> response = api.apiGet(OGCMediaTypeMapper.json.toString());

        assertEquals(HttpStatus.TEMPORARY_REDIRECT, response.getStatusCode(), "Incorrect redirect");
        assertEquals("/api/v1/ogc/api-docs/v3", Objects.requireNonNull(response.getHeaders().getLocation()).getPath(), "Incorrect path");

        response = api.apiGet(OGCMediaTypeMapper.html.toString());

        assertEquals(HttpStatus.TEMPORARY_REDIRECT, response.getStatusCode(), "Incorrect redirect");
        assertEquals("/api/v1/ogc/swagger-ui/index.html", Objects.requireNonNull(response.getHeaders().getLocation()).getPath(), "Incorrect path");
    }

    @Test
    @Order(1)
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }
    /**
     * The search is a fuzzy search based on title and description and few param field. So you expect 1 hit only,
     * we check via getTotal() which is value from another elastic search.
     * @throws IOException - IO Exception
     */
    @Test
    public void verifyApiCollectionsQueryOnText1() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=reproduction", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getTotal(), "Only 1 hit");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");

        // This time we make a typo but we should still get the result back as Fuzzy search
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=temperatura", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getTotal(), "Only 1 hit");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 7709f541-fc0c-4318-b5b9-9053aa474e0e");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=temperatura,reproduction", ExtendedCollections.class);
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getTotal(), "hit 2");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 7709f541-fc0c-4318-b5b9-9053aa474e0e");
    }
    /**
     * The search is a fuzzy search based on title and description and few param field. This test add one more text
     * which should hit the organization, paramter or vocab field. We use getTotal() instead of count the collection list
     * therefore we can verify this function call too. The getTotal() value comes from elastic count CountRequest.
     * 516811d7-cd1e-207a-e0440003ba8c79dd - Have repoduction
     * 073fde5a-bff3-1c1f-e053-08114f8c5588 - Nothing match (although the word 'and' will match, but we use AND operator in fuzzy match so it will not count)
     * 9fdb1eee-bc28-43a9-88c5-972324784837 - Contains 'precipitation and evaporation' in parameter_vocabs
     *
     * @throws IOException - IO Exception
     */
    @Test
    public void verifyApiCollectionsQueryOnText2() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "073fde5a-bff3-1c1f-e053-08114f8c5588.json",
                "9fdb1eee-bc28-43a9-88c5-972324784837.json"
        );

        // Call rest api directly and get query result
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=reproduction,precipitation and evaporation", ExtendedCollections.class);
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getTotal(), "Only 2 hit");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 516811d7-cd1e-207a-e0440003ba8c79dd");

        assertEquals(
                "9fdb1eee-bc28-43a9-88c5-972324784837",
                collections.getBody().getCollections().get(1).getId(),
                "Correct UUID - 9fdb1eee-bc28-43a9-88c5-972324784837");
    }
    /**
     * The datetime field after xxx/.. xxx/ etc. It uses CQL internally so no need to test Before After During in CQL
     */
    @Test
    public void verifyDateTimeAfterBounds() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<ExtendedCollections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1994-02-16T13:00:00Z/..", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1 because 1 document do not have start date");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");

        // The syntax slightly diff but they are the same / = /.. the time is slightly off with one of the record so still get 1
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T15:10:44Z/", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1 because 1 document do not have start date");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");

        // The syntax slightly diff but they are the same / = /.. the time is slightly off with one of the record so still get 1
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T14:10:44Z/", ExtendedCollections.class);
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 2");
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
     * The datetime field before ../xxx or /xxx etc. It uses CQL internally so no need to test Before After During in CQL
     */
    @Test
    public void verifyDateTimeBeforeBounds() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(3, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit all");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/2007-06-05T14:00:00Z", Collections.class);
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 2 only given this time");
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
     * The datetime field before xxx/yyy. It uses CQL internally so no need to test Before After During in CQL
     */
    @Test
    public void verifyDateTimeBetweenBounds() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1870-07-16T14:10:44Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 2, one record no start date");
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
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - 5c418118-2581-4936-b6fd-d6bedfe74f62");
    }
    /**
     * One of the record in the dataset contains two start/end date in the temporal field. It uses CQL internally
     * so no need to test Before After During in CQL
     * @throws IOException - Not expected
     */
    @Test
    public void verifyDateTimeBoundsWithDiscreteTime() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a.json"     // This one have two start/end
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=2006-02-28T13:00:00Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        // The start datetime is 1 sec more then one of the start date in the record, however it still fit into the next start/end slot, so should return same result
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=1991-12-31T13:00:01Z/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        // Now we check the before which should include the same record as it match one of the start/end time
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/1995-03-30T13:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, this record have 2 start/end");
        assertEquals(
                "caf7220a-19e0-4a7f-9af6-eade6c79a47a",
                collections.getBody().getCollections().get(0).getId(),
                "Correct UUID - caf7220a-19e0-4a7f-9af6-eade6c79a47a");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=/2013-06-16T14:00:00Z", Collections.class);
        // There are only 3 docs
        assertEquals(3, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 3, this record have 2 start/end");
    }
    /**
     * The properties param control what properties should be return to improve the speed of data transfer between component.
     */
    @Test
    public void verifyPropertiesParameter() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2007-06-05T14:00:00Z&properties=id,title", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");

        assertNotNull(collections.getBody().getCollections().get(0).getId());
        assertNotNull(collections.getBody().getCollections().get(0).getTitle());
        assertNull(collections.getBody().getCollections().get(0).getDescription());

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?datetime=../2007-06-05T14:00:00Z&properties=id,title,description", Collections.class);
        assertNotNull(Objects.requireNonNull(collections.getBody()).getCollections().get(0).getDescription());
    }
    /**
     * Check Common Query Language behavior is null / is not null
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyIsNullIsNotNull() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",    // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider not null
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NOT NULL", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");
    }
    /**
     * Test the equals with clause
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyEqualsOperation() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",   // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider is IMOS
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider='IMOS'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");
    }
    /**
     * Verify AND operation for CQL
     *
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyAndOperation() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",   // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider is IMOS
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider='IMOS'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL AND dataset_provider='IMOS'", Collections.class);
        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "nothing will hit with and ");
    }
    /**
     * Verify text search match with phase, that is the order of the text should appear in the title
     */
    @Test
    public void verifyParameterTextSearchMatch() throws IOException  {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",   // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider is IMOS
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=title='Impacts of stress on coral reproduction.'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=title='stress on coral reproduction.'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, still partial match");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=title='on stress coral reproduction.'", Collections.class);
        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 0, order of words diff");
    }

    @Test
    public void verifyParameterParameterVocabsSearchMatch() throws IOException  {
        super.insertJsonToElasticRecordIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=parameter_vocabs='wave'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record in wave");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=parameter_vocabs='alkalinity' AND parameter_vocabs='temperature'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, 1 record belong to both parameter vocabs");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=parameter_vocabs='wave' AND parameter_vocabs='temperature'", Collections.class);
        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 0, no records belong to both parameter vocabs");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=parameter_vocabs='wave' OR parameter_vocabs='temperature'", Collections.class);
        assertEquals(3, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 3, 1 in wave and 2 in temperature");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=parameter_vocabs='this parameter vocab does not exist'", Collections.class);
        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 0, parameter vocab none exist");
    }
    /**
     * Verify OR operation for CQL
     *
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyOrOperation() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",   // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider is IMOS
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider='IMOS'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "7709f541-fc0c-4318-b5b9-9053aa474e0e",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "516811d7-cd1e-207a-e0440003ba8c79dd",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_provider IS NULL OR dataset_provider='IMOS'", Collections.class);
        assertEquals(2, Objects.requireNonNull(collections.getBody()).getCollections().size(), "nothing will hit with and ");
    }
    /**
     * Verify INTERSECT CQL operation
     *
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyIntersectOperation() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc.json"
        );
        // Intersect with this polygon
        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(
                getBasePath() + "/collections?filter=INTERSECTS(geometry,POLYGON ((94.46973787472069 -21.134308721401936, 175.09692901649828 -21.134308721401936, 175.09692901649828 24.576866444501007, 94.46973787472069 24.576866444501007, 94.46973787472069 -21.134308721401936)))",
                Collections.class);

        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");
        // out of bounds with this polygon
        collections = testRestTemplate.getForEntity(
                getBasePath() + "/collections?filter=INTERSECTS(geometry,POLYGON ((82.29211035373572 -2.2497973160605653, 162.9193014955133 -2.2497973160605653, 162.9193014955133 40.78887880499494, 82.29211035373572 40.78887880499494, 82.29211035373572 -2.2497973160605653)))",
                Collections.class);

        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit none");
    }

    /**
     * Verify filter on attribute dataset_group works
     *
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyDatasetGroup() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",   // Provider null
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"             // Provider is IMOS
        );

        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=dataset_group='aodn'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");
        assertEquals(
                "5c418118-2581-4936-b6fd-d6bedfe74f62",
                collections.getBody().getCollections().get(0).getId(),
                "UUID matches");
    }
    /**
     * You can use the score to tune the return result's relevancy, at this moment, only >= make sense other value
     * will be ignored.
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLPropertyScore() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );
        // Make sure AND operation works
        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=score>=2 AND parameter_vocabs='wave'", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");

        // Make sure OR not work as it didn't make sense to use or with setting
        ResponseEntity<ErrorResponse> error = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=score>=2 OR parameter_vocabs='wave'", ErrorResponse.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertEquals("Or combine with query setting do not make sense", Objects.requireNonNull(error.getBody()).getMessage(), "correct error");

        // Lower score but the fuzzy is now with operator AND, therefore it will try to match all words 'dataset' and 'includes' with fuzzy
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q='dataset includes'&filter=score>=1", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, with score 3");
        assertEquals("bf287dfe-9ce4-4969-9c59-51c39ea4d011", Objects.requireNonNull(collections.getBody()).getCollections().get(0).getId(), "bf287dfe-9ce4-4969-9c59-51c39ea4d011");

        // Increase score will drop one record
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q='dataset includes'&filter=score>=3", Collections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, with score 3");
        assertEquals("bf287dfe-9ce4-4969-9c59-51c39ea4d011", Objects.requireNonNull(collections.getBody()).getCollections().get(0).getId(), "bf287dfe-9ce4-4969-9c59-51c39ea4d011");
    }

    /**
     * @throws IOException - Not expected
     */
    @Test
    public void verifyCQLFuzzyKey() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        ResponseEntity<ExtendedCollections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=(fuzzy_title='salinity')", ExtendedCollections.class);
        assertEquals(0, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 0 record with fuzzy_title");
        assertEquals(0, collections.getBody().getTotal());

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=(fuzzy_desc='salinity')", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record with fuzzy_description");
        assertEquals(1, collections.getBody().getTotal());

        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=(fuzzy_desc='salinity' or fuzzy_title='salinity')", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record with combined fuzzy_");
        assertEquals(1, collections.getBody().getTotal());
    }

    /**
     * You should receive ErrorMessage and as the id is not found, client side should expect ErrorResponse as the default message
     * on any error
     */
    @Test
    public void verifyErrorMessageCreated()  {
        ResponseEntity<String> collection = testRestTemplate.getForEntity(getBasePath() + "/collections/12324", String.class);

        assertEquals(HttpStatus.NOT_FOUND, collection.getStatusCode(), "Request Status match");
    }
    /**
     * Test sort by, you can use this as an example for how ot use sortBy
     */
    @Test
    public void verifySortBy() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json"
        );

        // Edge case on sort by with 1 item, but typo in argument sortBy, it should be sortby. Hence use API default sort -score
        // https://docs.ogc.org/DRAFTS/20-004.html#sorting-parameter-sortby
        ResponseEntity<ExtendedCollections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=score>=2 AND parameter_vocabs='wave'&sortBy=-score,+title", ExtendedCollections.class);
        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1, only one record");

        // Now return result should sort by score then title, since no query here, the score will auto adjust to 1 as all search without query default score is 1
        // the search result will be by title
        collections = testRestTemplate.getForEntity(getBasePath() + "/collections?filter=score>=2&sortby=-score,+title", ExtendedCollections.class);
        assertEquals(3, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 3");
        assertEquals(3, collections.getBody().getTotal(), "hit 3");
        assertEquals("19da2ce7-138f-4427-89de-a50c724f5f54", Objects.requireNonNull(collections.getBody()).getCollections().get(0).getId(), "19da2ce7-138f-4427-89de-a50c724f5f54");
        assertEquals("bf287dfe-9ce4-4969-9c59-51c39ea4d011", Objects.requireNonNull(collections.getBody()).getCollections().get(1).getId(), "bf287dfe-9ce4-4969-9c59-51c39ea4d011");
        assertEquals("7709f541-fc0c-4318-b5b9-9053aa474e0e", Objects.requireNonNull(collections.getBody()).getCollections().get(2).getId(), "7709f541-fc0c-4318-b5b9-9053aa474e0e");
    }
    /**
     * 073fde5a-bff3-1c1f-e053-08114f8c5588 contains a temporal which is ongoing so it should at top when sort by
     * desc, this is a handcraft record on temporal section
     *      [
     *       {
     *         "start": "2014-11-10T13:00:00Z",
     *         "end": "2014-11-16T13:00:00Z"
     *       },
     *       {
     *         "start": "2018-11-10T13:00:00Z",
     *         "end": null
     *       }
     *     ]
     * @throws IOException - Not expected
     */
    @Test
    public void verifySortByTemporalCorrect() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "073fde5a-bff3-1c1f-e053-08114f8c5588.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json",
                // This is a special case where temporal start and end is null
                "bc55eff4-7596-3565-e044-00144fdd4fa6.json",
                // This sample is important as it contains two end date where the first end is smaller than end
                // date in bf287dfe-9ce4-4969-9c59-51c39ea4d011, but then second end date is greater, so it
                // should rank upper due to second end time greater
                "bb3599d5-ab12-4278-a68b-42cac8e7a746.json",
                "bf287dfe-9ce4-4969-9c59-51c39ea4d011.json");

        // Call rest api directly and get query result
        ResponseEntity<Collections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections?sortby=-temporal",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(8, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 8");
        assertEquals("bc55eff4-7596-3565-e044-00144fdd4fa6", Objects.requireNonNull(collections.getBody()).getCollections().get(0).getId(), "null date is the greatest, bc55eff4-7596-3565-e044-00144fdd4fa6");
        assertEquals("073fde5a-bff3-1c1f-e053-08114f8c5588", Objects.requireNonNull(collections.getBody()).getCollections().get(1).getId(), "null date is the greatest, bc55eff4-7596-3565-e044-00144fdd4fa6");
        assertEquals("bf287dfe-9ce4-4969-9c59-51c39ea4d011", Objects.requireNonNull(collections.getBody()).getCollections().get(2).getId(), "bf287dfe-9ce4-4969-9c59-51c39ea4d011");
        assertEquals("19da2ce7-138f-4427-89de-a50c724f5f54", Objects.requireNonNull(collections.getBody()).getCollections().get(3).getId(), "19da2ce7-138f-4427-89de-a50c724f5f54");
        assertEquals("7709f541-fc0c-4318-b5b9-9053aa474e0e", Objects.requireNonNull(collections.getBody()).getCollections().get(4).getId(), "7709f541-fc0c-4318-b5b9-9053aa474e0e");
        assertEquals("516811d7-cd1e-207a-e0440003ba8c79dd", Objects.requireNonNull(collections.getBody()).getCollections().get(5).getId(), "516811d7-cd1e-207a-e0440003ba8c79dd");
        assertEquals("bb3599d5-ab12-4278-a68b-42cac8e7a746", Objects.requireNonNull(collections.getBody()).getCollections().get(6).getId(), "bb3599d5-ab12-4278-a68b-42cac8e7a746");
        assertEquals("5c418118-2581-4936-b6fd-d6bedfe74f62", Objects.requireNonNull(collections.getBody()).getCollections().get(7).getId(), "5c418118-2581-4936-b6fd-d6bedfe74f62");
    }
    /**
     * If this field exist, then
     */
    @Test
    public void verifyAssetSummarySearchWorks() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "073fde5a-bff3-1c1f-e053-08114f8c5588.json",
                "5c418118-2581-4936-b6fd-d6bedfe74f62.json",
                "19da2ce7-138f-4427-89de-a50c724f5f54.json",
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "35234913-aa3c-48ec-b9a4-77f822f66ef8.json" // This one have cloud optimized index, that is assets.summary value
        );

        ResponseEntity<Collections> collections = testRestTemplate.exchange(
                getBasePath() + "/collections?filter=(assets_summary IS NULL)&sortby=-temporal",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(4, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 4");

        collections = testRestTemplate.exchange(
                getBasePath() + "/collections?filter=(assets_summary IS NOT NULL)&sortby=-temporal",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(1, Objects.requireNonNull(collections.getBody()).getCollections().size(), "hit 1");
        assertEquals("35234913-aa3c-48ec-b9a4-77f822f66ef8", Objects.requireNonNull(collections.getBody()).getCollections().get(0).getId(), "asset.summary exist 35234913-aa3c-48ec-b9a4-77f822f66ef8");
    }
}
