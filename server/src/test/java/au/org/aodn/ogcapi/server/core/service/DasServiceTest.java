package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
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
import org.springframework.web.client.RestTemplate;

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
 * Unit tests for DasService URL building. Verifies that null date params are omitted (so they are
 * never passed to URI template expansion) and that buoy/mooring identifiers are sent as path
 * variables for single, correct encoding. Also covers the cloud-optimised size-estimate call
 * (request body shape, auth headers, and error propagation).
 */
public class DasServiceTest {

    private static final String HOST = "http://localhost:5001";

    private RestTemplate httpClient;
    private DasService dasService;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        DASConfig config = new DASConfig(HOST, "test-secret", "");

        dasService = new DasService();
        dasService.dasConfig = config;
        dasService.httpClient = httpClient;
        dasService.objectMapper = new ObjectMapper();
        dasService.init();

        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));
    }

    @SuppressWarnings("unchecked")
    private CapturedRequest captureMapRequest() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), mapCaptor.capture());
        return new CapturedRequest(urlCaptor.getValue(), mapCaptor.getValue());
    }

    @Test
    public void testBetweenDatesIncludesBothDateParams() {
        dasService.getWaveBuoysBetweenDates("2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z");

        CapturedRequest captured = captureMapRequest();
        assertTrue(captured.url.startsWith(HOST + "/api/v1/das/data/feature-collection/wave-buoy"));
        assertTrue(captured.url.contains("start_date={start_date}"));
        assertTrue(captured.url.contains("end_date={end_date}"));
        assertEquals("2024-01-01T00:00:00Z", captured.params.get("start_date"));
        assertEquals("2024-01-02T00:00:00Z", captured.params.get("end_date"));
    }

    @Test
    public void testBetweenDatesOmitsNullStart() {
        dasService.getWaveBuoysBetweenDates(null, "2024-01-02T00:00:00Z");

        CapturedRequest captured = captureMapRequest();
        assertFalse(captured.url.contains("start_date"), "null start_date must not appear in URL");
        assertFalse(captured.params.containsKey("start_date"), "null start_date must not be a uri variable");
        assertTrue(captured.url.contains("end_date={end_date}"));
        assertEquals("2024-01-02T00:00:00Z", captured.params.get("end_date"));
    }

    @Test
    public void testBetweenDatesOmitsBothNullDates() {
        dasService.getWaveBuoysBetweenDates(null, null);

        CapturedRequest captured = captureMapRequest();
        // No query string at all when both dates are absent
        assertFalse(captured.url.contains("?"), "no query params expected, got: " + captured.url);
        assertTrue(captured.params.isEmpty());
    }

    @Test
    public void testBuoyDetailsSendsRawBuoyAsPathVariable() {
        // A buoy name with a space must be passed raw; RestTemplate encodes the path variable once.
        dasService.getWaveBuoyDetailsBetweenDates("2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", "Apollo Bay");

        CapturedRequest captured = captureMapRequest();
        assertTrue(captured.url.contains("/wave-buoy/{buoy}"), "buoy must be a path variable, got: " + captured.url);
        assertEquals("Apollo Bay", captured.params.get("buoy"));
    }

    @Test
    public void testMooringDetailsSendsRawMooringAsPathVariable() {
        dasService.getMooringDetailsBetweenDates("2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", "Mooring 1");

        CapturedRequest captured = captureMapRequest();
        assertTrue(captured.url.contains("/mooring/{mooring}"), "mooring must be a path variable, got: " + captured.url);
        assertEquals("Mooring 1", captured.params.get("mooring"));
    }

    @Test
    public void testLatestAvailableDateUsesNoUriVariables() {
        dasService.getWaveBuoysLatestAvailableDate();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
        assertEquals(HOST + "/api/v1/das/data/feature-collection/wave-buoy/latest", urlCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private HttpEntity<Map<String, String>> callEstimateAndCaptureEntity(String uuid, Map<String, String> parameters) {

        // DAS streams the estimate: heartbeats while it computes, then the dict nested
        // under the terminal result event.
        String sseBody = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: result
                data: {"status":"completed","message":"Done","data":{"estimated_output_bytes":123}}

                """;
        when(httpClient.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class), anyMap()))
                .thenReturn(ResponseEntity.ok(sseBody));

        String result = dasService.estimateCloudOptimisedDownloadSize(uuid, parameters);
        assertEquals("{\"estimated_output_bytes\":123}", result, "The estimate dict should be unwrapped from the stream");

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> uriVarsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class), uriVarsCaptor.capture());

        assertEquals(HOST + "/api/v1/das/data/{uuid}/estimate_size", urlCaptor.getValue());
        assertEquals(uuid, uriVarsCaptor.getValue().get("uuid"));

        return (HttpEntity<Map<String, String>>) entityCaptor.getValue();
    }

    @Test
    public void testEstimatePostsBatchStyleParametersWithApiKey() {
        // The estimate forwards the same batch-style subset parameter map to DAS unchanged.
        Map<String, String> parameters = Map.of(
                "uuid", "test-uuid",
                "key", "a.zarr,b.zarr",
                "start_date", "2023-01-01",
                "end_date", "2023-01-31",
                "multi_polygon", "non-specified",
                "output_format", "netcdf");

        HttpEntity<Map<String, String>> entity = callEstimateAndCaptureEntity("test-uuid", parameters);

        HttpHeaders headers = entity.getHeaders();
        assertEquals("test-secret", headers.getFirst("X-API-KEY"));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));

        assertEquals(parameters, entity.getBody(), "The batch-style parameter map must be forwarded to DAS unchanged");
    }

    @Test
    public void testEstimateAcceptsEventStream() {
        HttpEntity<Map<String, String>> entity = callEstimateAndCaptureEntity(
                "test-uuid", Map.of("uuid", "test-uuid", "output_format", "netcdf"));

        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, entity.getHeaders().getFirst(HttpHeaders.ACCEPT));
    }

    @Test
    public void testEstimateErrorEventThrows() {
        // A failure raised after the stream opened comes back on an HTTP 200, so it is
        // only visible in the frames. It must still surface as a thrown exception,
        // otherwise the SSE layer would report a failed estimate as a successful one.
        String sseBody = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: error
                data: {"status":"error","message":"404: No matching keys found for uuid=bad-uuid"}

                """;
        when(httpClient.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class), anyMap()))
                .thenReturn(ResponseEntity.ok(sseBody));

        RuntimeException e = assertThrows(RuntimeException.class, () ->
                dasService.estimateCloudOptimisedDownloadSize(
                        "bad-uuid", Map.of("uuid", "bad-uuid", "key", "missing.zarr", "output_format", "netcdf")));

        assertEquals("404: No matching keys found for uuid=bad-uuid", e.getMessage(),
                "DAS's reason is forwarded verbatim for the SSE layer to report");
    }

    @Test
    public void testEstimateNon2xxPropagates() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class), anyMap()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThrows(HttpClientErrorException.class, () ->
                dasService.estimateCloudOptimisedDownloadSize(
                        "bad-uuid", Map.of("uuid", "bad-uuid", "key", "missing.zarr", "output_format", "netcdf")));
    }

    private record CapturedRequest(String url, Map<String, String> params) {
    }
}
