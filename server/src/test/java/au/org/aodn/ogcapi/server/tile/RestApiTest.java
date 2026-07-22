package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;
import au.org.aodn.ogcapi.server.core.service.das.DasTilerService;
import au.org.aodn.ogcapi.tile.model.InlineResponse2002;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RestApiTest extends BaseTestClass {

    @MockitoBean
    protected DasTilerService dasTilerService;

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
     * Verify api call /tiles
     */
    @Test
    public void verifyTiles() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        ResponseEntity<InlineResponse2002> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/tiles?f=json",
                InlineResponse2002.class
        );

        Assertions.assertNotNull(tiles.getBody(), "Body not null");
        Assertions.assertNotNull(tiles.getBody().getTilesets(), "Tilesets not null");
        Assertions.assertEquals(2, tiles.getBody().getTilesets().size(), "Count is correct");
        Assertions.assertEquals("Impacts of stress on coral reproduction.", tiles.getBody().getTilesets().get(0).getTitle(), "Title matched 1");
        Assertions.assertEquals("Ocean acidification historical reconstruction", tiles.getBody().getTilesets().get(1).getTitle(), "Title matched 2");
    }
    /**
     * Verify api call /collections/{collectionId}/tiles
     */
    @Test
    public void verifyCollectionTiles() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "516811d7-cd1e-207a-e0440003ba8c79dd.json",
                "7709f541-fc0c-4318-b5b9-9053aa474e0e.json"
        );

        ResponseEntity<InlineResponse2002> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/collections/516811d7-cd1e-207a-e0440003ba8c79dd/tiles",
                InlineResponse2002.class
        );

        Assertions.assertNotNull(tiles.getBody(), "Body not null");
        Assertions.assertNotNull(tiles.getBody().getTilesets(), "Tilesets not null");
        Assertions.assertEquals(1, tiles.getBody().getTilesets().size(), "Count is correct");
        Assertions.assertEquals("Impacts of stress on coral reproduction.", tiles.getBody().getTilesets().get(0).getTitle(), "Title matched 1");
    }
    /**
     * Verify api call /tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}, this call will return the bounding
     * box using mvt search, however this mvt search require licence and not enable by default. Without UI it is
     * very hard to enable it.
     */
    @Test
    public void verifyTilesMatrixSetXYZ() throws IOException {
        super.insertJsonToElasticRecordIndex(
                "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc.json"
        );

        ResponseEntity<byte[]> tiles = testRestTemplate.getForEntity(
                getBasePath() + "/tiles/WebMercatorQuad/2/0/1?collections=b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc",
                byte[].class
        );

        // No meaningful verify can be done here, we keep it here so that we know that test case considered.
    }

    /**
     * Tests for the DAS visual-tile proxy route
     * /collections/{collectionId}/map/tiles/WebMercatorQuad/{z}/{x}/{y}. dasTilerService is
     * mocked so these don't need a running DAS — they verify routing (the @Hidden generated
     * stub must not shadow the hand-written mapping), param validation, and status mirroring.
     */
    @Test
    public void verifyVisualMapTileMissingProductReturns400() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/1?datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    /**
     * Rejections go through ApiRequestException -> GlobalExceptionHandler, so they must render the
     * same ErrorResponse envelope every other handled error in this service uses, not an ad-hoc body.
     */
    @Test
    public void verifyVisualMapTileRejectionUsesErrorResponseEnvelope() {
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/1?datetime=2024-01-01",
                ErrorResponse.class
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertNotNull(response.getBody(), "Body not null");
        Assertions.assertEquals("product is required", response.getBody().getMessage(), "Message carries the reason");
        Assertions.assertNotNull(response.getBody().getTimestamp(), "Timestamp populated by the handler");
        Assertions.assertTrue(
                response.getBody().getDetails().contains("/collections/some-uuid/map/tiles"),
                "Details identify the failing request, got: " + response.getBody().getDetails());
    }

    @Test
    public void verifyVisualMapTileMissingDatetimeReturns400() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/1?product=p1",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileBadDatetimeReturns400() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/1?product=p1&datetime=2024-01-1",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileWrongTileMatrixSetReturns404() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WorldCRS84Quad/2/1/1?product=p1&datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileNegativeZoomReturns400() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/-1/0/0?product=p1&datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileZoomAboveMaxReturns400() {
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/25/0/0?product=p1&datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileRowColOutOfRangeForZoomReturns400() {
        // At z=2 valid row/col range is 0-3; 4 is one past the edge.
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/4/0?product=p1&datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileMaxZoomBoundaryIsAccepted() {
        when(dasTilerService.isProductInCollection("some-uuid", "p1")).thenReturn(true);
        when(dasTilerService.getVisualTile(eq("p1"), eq("2024-01-01"), eq(24), eq(0), eq(0), eq("png"), isNull(), isNull()))
                .thenReturn(new DasTilerService.DasTileResult("tile-bytes".getBytes(), "image/png", null));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/24/0/0?product=p1&datetime=2024-01-01",
                byte[].class
        );
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileProductNotInCollectionReturns404() {
        // isProductInCollection defaults to false on the mock (unstubbed boolean).
        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/1?product=p1&datetime=2024-01-01",
                String.class
        );
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void verifyVisualMapTileForwardsZXYAndReturnsImage() {
        when(dasTilerService.isProductInCollection("some-uuid", "p1")).thenReturn(true);
        when(dasTilerService.getVisualTile(eq("p1"), eq("2024-01-01"), eq(2), eq(1), eq(3), eq("png"), isNull(), isNull()))
                .thenReturn(new DasTilerService.DasTileResult("tile-bytes".getBytes(), "image/png", "public, max-age=31536000, immutable"));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/3?product=p1&datetime=2024-01-01",
                byte[].class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertArrayEquals("tile-bytes".getBytes(), response.getBody());
        Assertions.assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
    }

    @Test
    public void verifyVisualMapTileFormatWebpMapsToWebpExt() {
        when(dasTilerService.isProductInCollection("some-uuid", "p1")).thenReturn(true);
        when(dasTilerService.getVisualTile(eq("p1"), eq("2024-01-01"), eq(2), eq(1), eq(3), eq("webp"), isNull(), isNull()))
                .thenReturn(new DasTilerService.DasTileResult("webp-bytes".getBytes(), "image/webp", null));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/3?product=p1&datetime=2024-01-01&f=webp",
                byte[].class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertArrayEquals("webp-bytes".getBytes(), response.getBody());
    }

    @Test
    public void verifyVisualMapTileUpstreamErrorMirrored() {
        when(dasTilerService.isProductInCollection("some-uuid", "p1")).thenReturn(true);
        when(dasTilerService.getVisualTile(eq("p1"), eq("2024-01-01"), eq(2), eq(1), eq(3), eq("png"), isNull(), isNull()))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "no such date"));

        ResponseEntity<String> response = testRestTemplate.getForEntity(
                getBasePath() + "/collections/some-uuid/map/tiles/WebMercatorQuad/2/1/3?product=p1&datetime=2024-01-01",
                String.class
        );

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

}
