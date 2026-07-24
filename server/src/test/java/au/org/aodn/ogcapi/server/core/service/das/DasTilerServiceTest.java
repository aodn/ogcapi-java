package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.stac.model.AssetModel;
import au.org.aodn.stac.model.StacCollectionModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DasTilerService: URL building for product ids containing ':'/'+', null-param
 * omission, upstream status mapping, and collection-membership filtering. The API key itself is
 * attached by the RestTemplate bean, so it is covered by ConfigTest rather than here.
 * No Spring context / no Docker — mirrors DasServiceTest's style.
 */
public class DasTilerServiceTest {

    private static final String HOST = "http://localhost:5000";
    private static final String PRODUCT_ID = "model_sea_level_anomaly_gridded_realtime:gsla";

    private RestTemplate httpClient;
    private Search search;
    private DasTilerService service;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);
        search = mock(Search.class);

        DasProperties config = new DasProperties(
                HOST, "test-secret", "internal-secret",
                Duration.ofSeconds(5), Duration.ofSeconds(30)
        );

        service = new DasTilerService(config, search, httpClient, new ObjectMapper());
    }

    private HttpHeaders imageHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private CapturedRequest captureImageRequest() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).getForEntity(urlCaptor.capture(), eq(byte[].class), mapCaptor.capture());
        return new CapturedRequest(urlCaptor.getValue(), mapCaptor.getValue());
    }

    @Test
    public void testGetVisualTileSendsProductAsPathVariable() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        CapturedRequest captured = captureImageRequest();
        assertTrue(captured.url.contains("/{product}/{date}/{z}/{x}/{y}.{ext}"), "product must be a path variable, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"), "product id with ':' must be passed raw as a path variable");
    }

    @Test
    public void testGetVisualTileOmitsNullColormapAndRescale() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        CapturedRequest captured = captureImageRequest();
        assertFalse(captured.url.contains("colormap"), "null colormap must not appear in URL");
        assertFalse(captured.url.contains("rescale"), "null rescale must not appear in URL");
    }

    @Test
    public void testGetVisualTileIncludesColormapAndRescaleWhenProvided() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", "viridis", "-1,1");

        CapturedRequest captured = captureImageRequest();
        assertTrue(captured.url.contains("colormap={colormap}"));
        assertTrue(captured.url.contains("rescale={rescale}"));
        assertEquals("viridis", captured.params.get("colormap"));
        assertEquals("-1,1", captured.params.get("rescale"));
    }

    @Test
    public void testGetVisualTileForwardsContentTypeAndCacheControl() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        DasTilerService.DasTileResult result = service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        assertArrayEquals("tile-bytes".getBytes(), result.body());
        assertEquals("image/png", result.contentType());
        assertEquals("public, max-age=31536000, immutable", result.cacheControl());
    }

    @Test
    public void testUpstreamBadRequestMirroredAsIs() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        "{\"detail\":\"bad date\"}".getBytes(), null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "bad-date", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("bad date", ex.getMessage(), "DAS's own explanation is actionable, so it is forwarded");
    }

    @Test
    public void testUpstreamNotFoundMirroredAsIs() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Not Found", ex.getMessage(), "empty upstream body falls back to the status reason phrase");
    }

    @Test
    public void testUpstreamDetailIsTakenButRawBodyIsNot() {
        // Only the `detail` field may cross the boundary — anything else DAS includes must not.
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "{\"detail\":\"no data for that date\",\"source_path\":\"s3://internal/secret.zarr\"}".getBytes(),
                        null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals("no data for that date", ex.getMessage());
        assertFalse(ex.getMessage().contains("s3://"), "upstream internals must not reach the client");
    }

    @Test
    public void testUpstreamServiceUnavailableMirroredAsIs() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    public void testUpstreamUnauthorizedMappedTo502() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus(), "misconfigured secret is our fault, not the client's — must not leak as 401");
        String message = ex.getMessage().toLowerCase();
        assertFalse(message.contains("auth") || message.contains("unauthorized") || message.contains("key"),
                "must not tell an unauthenticated caller that our credentials were rejected, got: " + ex.getMessage());
    }

    @Test
    public void testUpstreamOtherServerErrorMappedTo502() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    public void testTimeoutMappedTo504() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("read timed out")));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }

    @Test
    public void testOtherNetworkFailureMappedTo502() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(new ResourceAccessException("connection refused", new java.net.ConnectException()));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    public void testProductsForCollectionFiltersByMetadataUuid() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode products = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "p1").put("metadata_uuid", "uuid-a"))
                .add(mapper.createObjectNode().put("id", "p2").put("metadata_uuid", "uuid-b"))
                .add(mapper.createObjectNode().put("id", "p3"));
        when(httpClient.getForObject(anyString(), eq(JsonNode.class)))
                .thenReturn(products);

        assertEquals(1, service.productsForCollection("uuid-a").size());
        assertEquals("p1", service.productsForCollection("uuid-a").get(0).get("id").asText());
        assertTrue(service.productsForCollection("unknown-uuid").isEmpty());
    }

    @Test
    public void testIsDatasetInCollectionChecksAssetKeys() {
        // es-indexer keys assets by the cloud-optimised file name, which carries a format extension,
        // while DAS product ids use the bare stem — so everything from the first dot on is dropped
        // before matching. Membership is a stem lookup against the cached searchCollections result.
        // The extension is not enumerated, so an unknown/future format works the same way.
        StacCollectionModel model = StacCollectionModel.builder()
                .uuid("uuid-a")
                .assets(Map.of(
                        "satellite_austemp_heatwave_8day.zarr",
                        AssetModel.builder().role(AssetModel.Role.SUMMARY).build(),
                        "mooring_temperature_logger_delayed.parquet",
                        AssetModel.builder().role(AssetModel.Role.SUMMARY).build(),
                        "some_future_format_dataset.nc4",
                        AssetModel.builder().role(AssetModel.Role.SUMMARY).build()))
                .build();
        ElasticSearchBase.SearchResult<StacCollectionModel> found = new ElasticSearchBase.SearchResult<>();
        found.setCollections(List.of(model));
        when(search.searchCollections("uuid-a")).thenReturn(found);

        assertTrue(service.isDatasetInCollection("uuid-a", "satellite_austemp_heatwave_8day"),
                "the .zarr extension on the asset key must be ignored when matching the dataset");
        assertTrue(service.isDatasetInCollection("uuid-a", "mooring_temperature_logger_delayed"),
                "the .parquet extension on the asset key must be ignored when matching the dataset");
        assertTrue(service.isDatasetInCollection("uuid-a", "some_future_format_dataset"),
                "the extension is not hard-coded, so an unknown format is stripped the same way");
        assertFalse(service.isDatasetInCollection("uuid-a", "some_other_dataset"),
                "a dataset that is not an asset key is not in the collection");
    }

    @Test
    public void testIsDatasetInCollectionFalseWhenCollectionMissing() {
        ElasticSearchBase.SearchResult<StacCollectionModel> empty = new ElasticSearchBase.SearchResult<>();
        empty.setCollections(List.of());
        when(search.searchCollections("uuid-missing")).thenReturn(empty);

        assertFalse(service.isDatasetInCollection("uuid-missing", "model_sea_level_anomaly_gridded_realtime"));
    }

    private record CapturedRequest(String url, Map<String, Object> params) {
    }
}
