package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DasTilerService: URL building for product ids containing ':'/'+', null-param
 * omission, X-API-KEY header, upstream status mapping, and collection-membership filtering.
 * No Spring context / no Docker — mirrors DasServiceTest's style.
 */
public class DasTilerServiceTest {

    private static final String HOST = "http://localhost:5000";
    private static final String PRODUCT_ID = "model_sea_level_anomaly_gridded_realtime:gsla";

    private RestTemplate httpClient;
    private DasTilerService service;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        DasProperties config = new DasProperties(
                HOST, "test-secret", "internal-secret",
                new DasProperties.Tiler(Duration.ofSeconds(5), Duration.ofSeconds(30))
        );

        service = new DasTilerService(config, httpClient, new ObjectMapper());
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
        verify(httpClient).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), mapCaptor.capture());
        return new CapturedRequest(urlCaptor.getValue(), mapCaptor.getValue());
    }

    @Test
    public void testGetVisualTileSendsProductAsPathVariable() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        CapturedRequest captured = captureImageRequest();
        assertTrue(captured.url.contains("/{product}/{date}/{z}/{x}/{y}.{ext}"), "product must be a path variable, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"), "product id with ':' must be passed raw as a path variable");
    }

    @Test
    public void testGetVisualTileOmitsNullColormapAndRescale() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        CapturedRequest captured = captureImageRequest();
        assertFalse(captured.url.contains("colormap"), "null colormap must not appear in URL");
        assertFalse(captured.url.contains("rescale"), "null rescale must not appear in URL");
    }

    @Test
    public void testGetVisualTileIncludesColormapAndRescaleWhenProvided() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        DasTilerService.DasTileResult result = service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        assertArrayEquals("tile-bytes".getBytes(), result.body());
        assertEquals("image/png", result.contentType());
        assertEquals("public, max-age=31536000, immutable", result.cacheControl());
    }

    @Test
    public void testInitSetsApiKeyHeader() {
        // init() builds the shared HttpEntity; verify it carries the configured secret by
        // triggering any call and inspecting the entity Mockito captured.
        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("x".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        HttpHeaders sentHeaders = entityCaptor.getValue().getHeaders();
        assertEquals("test-secret", sentHeaders.getFirst("X-API-KEY"));
        assertEquals("internal-secret", sentHeaders.getFirst("x-internal-das-header-secret"));
    }

    @Test
    public void testUpstreamBadRequestMirroredAsIs() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    public void testUpstreamUnauthorizedMappedTo502() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    public void testTimeoutMappedTo504() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("read timed out")));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }

    @Test
    public void testOtherNetworkFailureMappedTo502() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
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
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(products));

        assertEquals(1, service.productsForCollection("uuid-a").size());
        assertEquals("p1", service.productsForCollection("uuid-a").get(0).get("id").asText());
        assertTrue(service.productsForCollection("unknown-uuid").isEmpty());
    }

    @Test
    public void testIsProductInCollection() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode products = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "p1").put("metadata_uuid", "uuid-a"));
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(products));

        assertTrue(service.isProductInCollection("uuid-a", "p1"));
        assertFalse(service.isProductInCollection("uuid-a", "p2"));
        assertFalse(service.isProductInCollection("uuid-other", "p1"));
    }

    private record CapturedRequest(String url, Map<String, Object> params) {
    }
}
