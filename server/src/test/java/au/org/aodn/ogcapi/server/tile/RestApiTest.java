package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.tile.model.InlineResponse2002;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RestApiTest extends BaseTestClass {

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
     * Verify api call /tiles
     */
    @Test
    public void verifyTiles() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        ResponseEntity<InlineResponse2002> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/tiles?f=json",
                InlineResponse2002.class
        );

        assertEquals(2, tiles.getBody().getTilesets().size(), "Count is correct");
        assertEquals("Impacts of stress on coral reproduction.", tiles.getBody().getTilesets().get(0).getTitle(), "Title matched 1");
        assertEquals("Ocean acidification historical reconstruction", tiles.getBody().getTilesets().get(1).getTitle(), "Title matched 2");
    }
    /**
     * Verify api call /collections/{collectionId}/tiles
     */
    @Test
    public void verifyCollectionTiles() throws IOException {
        super.insertJsonToElasticIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        ResponseEntity<InlineResponse2002> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/collections/516811d7-cd1e-207a-e0440003ba8c79dd/tiles",
                InlineResponse2002.class
        );

        assertEquals(1, tiles.getBody().getTilesets().size(), "Count is correct");
        assertEquals("Impacts of stress on coral reproduction.", tiles.getBody().getTilesets().get(0).getTitle(), "Title matched 1");
    }
    /**
     * Verify api call /tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}, this call will return the bounding
     * box using mvt search, however this mvt search require licence and not enable by default. Without UI it is
     * very hard to enable it.
     */
    @Test
    public void verifyTilesMatrixSetXYZ() throws IOException {
        super.insertJsonToElasticIndex(
                "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc.json"
        );

        ResponseEntity<byte[]> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/tiles/WebMercatorQuad/2/0/1?collections=b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc",
                byte[].class
        );

        // No meaningful verify can be done here, we keep it here so that we know that test case considered.
    }

}
