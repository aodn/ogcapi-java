package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private DasTilerService service;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        DasProperties config = new DasProperties(
                HOST, null,"test-secret", "internal-secret",
                Duration.ofSeconds(5), Duration.ofSeconds(30)
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
        verify(httpClient).getForEntity(urlCaptor.capture(), eq(byte[].class), mapCaptor.capture());
        return new CapturedRequest(urlCaptor.getValue(), mapCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private CapturedRequest captureJsonRequest() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).getForEntity(urlCaptor.capture(), eq(JsonNode.class), mapCaptor.capture());
        return new CapturedRequest(urlCaptor.getValue(), mapCaptor.getValue());
    }

    @Test
    public void testGetVisualTileSendsProductAsPathVariable() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null);

        CapturedRequest captured = captureImageRequest();
        assertTrue(captured.url.contains("/{product}/{z}/{x}/{y}.{ext}"), "product must be a path variable, got: " + captured.url);
        assertTrue(captured.url.contains("date={date}"), "date must be a query param, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"), "product id with ':' must be passed raw as a path variable");
        assertEquals("2024-01-01", captured.params.get("date"));
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

    // --- Data tiles: value-encoded PNGs, product-local LOD, date as the only query param ---

    @Test
    public void testGetDataTileSendsProductAsPathVariableAndDateAsQuery() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("data-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getDataTile(PRODUCT_ID, "2024-01-01", 1, 0, 0);

        CapturedRequest captured = captureImageRequest();
        assertTrue(captured.url.contains("/data_tiles/{product}/{z}/{x}/{y}.png"),
                "data tile must expand product/lod/x/y as path variables and end in .png, got: " + captured.url);
        assertTrue(captured.url.contains("date={date}"), "date must be a query param, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"), "product id with ':' must be a raw path variable");
        assertEquals("2024-01-01", captured.params.get("date"));
        assertEquals(1, captured.params.get("z"));
        assertEquals(0, captured.params.get("x"));
        assertEquals(0, captured.params.get("y"));
    }

    @Test
    public void testGetDataTilePassesMultiVariableProductIdRawInPath() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("data-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        String vectorProduct = "model_currents:ucur+vcur";
        service.getDataTile(vectorProduct, "2024-01-01", 1, 0, 0);

        CapturedRequest captured = captureImageRequest();
        // The '+' must ride into the path as a raw variable — RestTemplate encodes it to %2B on the
        // wire. If it were baked into the URL template it would decode back to a space at DAS.
        assertEquals(vectorProduct, captured.params.get("product"),
                "product id with '+' must be passed raw as a path variable, not pre-encoded");
    }

    @Test
    public void testGetDataTileForwardsContentTypeAndCacheControl() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("data-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        DasTilerService.DasTileResult result = service.getDataTile(PRODUCT_ID, "2024-01-01", 1, 0, 0);

        assertArrayEquals("data-bytes".getBytes(), result.body());
        assertEquals("image/png", result.contentType());
        assertEquals("public, max-age=31536000, immutable", result.cacheControl());
    }

    @Test
    public void testGetDataTileNotFoundMirrored() {
        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "{\"detail\":\"LOD 9 not in grid\"}".getBytes(), null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getDataTile(PRODUCT_ID, "2024-01-01", 9, 0, 0));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("LOD 9 not in grid", ex.getMessage());
    }

    // --- Point: decoded value(s) at a lat/lon, JSON body with query params ---

    @Test
    public void testGetPointSendsProductAsPathVariableWithDateLatLonQuery() {
        ObjectNode pointBody = new ObjectMapper().createObjectNode();
        pointBody.put("lat", -44.27813720703125);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenReturn(new ResponseEntity<>(pointBody, headers, HttpStatus.OK));

        service.getPoint(PRODUCT_ID, "2024-01-01", -44.27, 132.00);

        CapturedRequest captured = captureJsonRequest();
        assertTrue(captured.url.contains("/data_tiles/{product}/point"),
                "point must expand product as a path variable, got: " + captured.url);
        assertTrue(captured.url.contains("date={date}") && captured.url.contains("lat={lat}") && captured.url.contains("lon={lon}"),
                "date/lat/lon must be query params, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"), "product id with ':' must be a raw path variable");
        assertEquals("2024-01-01", captured.params.get("date"));
        assertEquals(-44.27, captured.params.get("lat"));
        assertEquals(132.00, captured.params.get("lon"));
    }

    @Test
    public void testGetPointForwardsBodyAndCacheControl() {
        ObjectNode pointBody = new ObjectMapper().createObjectNode();
        pointBody.put("lat", -44.27813720703125);
        pointBody.put("lon", 132.0092315673828);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenReturn(new ResponseEntity<>(pointBody, headers, HttpStatus.OK));

        DasTilerService.DasJsonResult result = service.getPoint(PRODUCT_ID, "2024-01-01", -44.27, 132.00);

        assertEquals(pointBody, result.body());
        assertEquals("public, max-age=31536000, immutable", result.cacheControl());
    }

    @Test
    public void testGetPointNotFoundMirrored() {
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "{\"detail\":\"point outside coverage\"}".getBytes(), null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getPoint(PRODUCT_ID, "2024-01-01", -89.0, 0.0));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("point outside coverage", ex.getMessage());
    }

    // --- Data manifest: JSON body, but (unlike the plain getters) forwards Cache-Control ---

    @Test
    public void testGetDataManifestBuildsUrlAndForwardsCacheControl() {
        ObjectNode manifestBody = new ObjectMapper().createObjectNode();
        manifestBody.putArray("bounds").add(0).add(0).add(1).add(1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenReturn(new ResponseEntity<>(manifestBody, headers, HttpStatus.OK));

        DasTilerService.DasJsonResult result = service.getDataManifest(PRODUCT_ID, "2024-01-01");

        CapturedRequest captured = captureJsonRequest();
        assertTrue(captured.url.contains("/data_tiles/{product}/manifest.json"),
                "manifest must expand product as a path variable and end in manifest.json, got: " + captured.url);
        assertTrue(captured.url.contains("date={date}"), "date must be a query param, got: " + captured.url);
        assertEquals(PRODUCT_ID, captured.params.get("product"));
        assertEquals("2024-01-01", captured.params.get("date"));
        assertEquals(manifestBody, result.body());
        assertEquals("public, max-age=31536000, immutable", result.cacheControl(),
                "the immutable Cache-Control must be forwarded, unlike the plain JSON getters");
    }

    @Test
    public void testGetDataManifestNotFoundMirroredWithDetail() {
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "{\"detail\":\"no data for that date\"}".getBytes(), null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getDataManifest(PRODUCT_ID, "2024-01-01"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("no data for that date", ex.getMessage());
    }

    @Test
    public void testGetDataManifestTimeoutMappedTo504() {
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("read timed out")));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getDataManifest(PRODUCT_ID, "2024-01-01"));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }

    @Test
    public void testGetDataManifestServerErrorMappedTo502() {
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getDataManifest(PRODUCT_ID, "2024-01-01"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
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

    // --- Product/date manifest (visual_tiles): JSON body, forwards whatever Cache-Control DAS sent ---

    @Test
    public void testGetManifestBuildsUrlAndForwardsCacheControl() {
        ObjectNode manifestBody = new ObjectMapper().createObjectNode();
        manifestBody.putObject("products");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=60");
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenReturn(new ResponseEntity<>(manifestBody, headers, HttpStatus.OK));

        DasTilerService.DasJsonResult result = service.getManifest(null);

        CapturedRequest captured = captureJsonRequest();
        assertTrue(captured.url.contains("/visual_tiles/manifest"), "got: " + captured.url);
        assertEquals(manifestBody, result.body());
        // Whatever freshness DAS decided must ride through as-is — this service must not invent
        // its own Cache-Control for a response it doesn't own the freshness of.
        assertEquals("public, max-age=60", result.cacheControl());
    }

    @Test
    public void testGetManifestServerErrorMappedTo502() {
        when(httpClient.getForEntity(anyString(), eq(JsonNode.class), anyMap()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));

        DasUpstreamException ex = assertThrows(DasUpstreamException.class, () -> service.getManifest(null));

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
    public void testProductsForCollectionsFiltersByGivenIds() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode products = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "p1").put("metadata_uuid", "uuid-a"))
                .add(mapper.createObjectNode().put("id", "p2").put("metadata_uuid", "uuid-b"))
                .add(mapper.createObjectNode().put("id", "p3").put("metadata_uuid", "uuid-c"));
        when(httpClient.getForObject(anyString(), eq(JsonNode.class)))
                .thenReturn(products);

        List<JsonNode> result = service.productsForCollections(List.of("uuid-a", "uuid-c"));

        assertEquals(2, result.size());
        assertEquals("p1", result.get(0).get("id").asText());
        assertEquals("p3", result.get(1).get("id").asText());
    }

    @Test
    public void testProductsForCollectionsWithNullOrEmptyIdsReturnsEverything() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode products = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "p1").put("metadata_uuid", "uuid-a"))
                .add(mapper.createObjectNode().put("id", "p2").put("metadata_uuid", "uuid-b"));
        when(httpClient.getForObject(anyString(), eq(JsonNode.class)))
                .thenReturn(products);

        assertEquals(2, service.productsForCollections(null).size());
        assertEquals(2, service.productsForCollections(List.of()).size());
    }

    private record CapturedRequest(String url, Map<String, Object> params) {
    }
}
