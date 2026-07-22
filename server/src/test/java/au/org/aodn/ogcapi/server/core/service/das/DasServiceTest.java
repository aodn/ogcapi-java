package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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
 * Unit tests for DasService URL building. Verifies that null date params are omitted (so they are
 * never passed to URI template expansion) and that buoy/mooring identifiers are sent as path
 * variables for single, correct encoding. Also covers the cloud-optimised size-estimate call
 * (request body shape and error propagation). The API key is attached by the RestTemplate bean,
 * so it is covered by ConfigTest rather than here.
 */
public class DasServiceTest {

    private static final String HOST = "http://localhost:5001";

    private RestTemplate httpClient;
    private DasService dasService;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        DasProperties config = new DasProperties(
                HOST, "test-secret", "",
                Duration.ofSeconds(5), Duration.ofSeconds(30)
        );

        dasService = new DasService(config, httpClient);

        when(httpClient.getForObject(anyString(), eq(byte[].class), anyMap()))
                .thenReturn("ok".getBytes());
        when(httpClient.getForObject(anyString(), eq(byte[].class)))
                .thenReturn("ok".getBytes());
    }

    @SuppressWarnings("unchecked")
    private CapturedRequest captureMapRequest() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).getForObject(urlCaptor.capture(), eq(byte[].class), mapCaptor.capture());
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
        verify(httpClient).getForObject(urlCaptor.capture(), eq(byte[].class));
        assertEquals(HOST + "/api/v1/das/data/feature-collection/wave-buoy/latest", urlCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEstimatePostsBatchStyleParametersUnchanged() {
        // The estimate forwards the same batch-style subset parameter map to DAS unchanged.
        Map<String, String> parameters = Map.of(
                "uuid", "test-uuid",
                "key", "a.zarr,b.zarr",
                "start_date", "2023-01-01",
                "end_date", "2023-01-31",
                "multi_polygon", "non-specified",
                "output_format", "netcdf");

        when(httpClient.postForObject(anyString(), any(), eq(String.class), anyMap()))
                .thenReturn("{\"estimated_output_bytes\":123}");

        String result = dasService.estimateCloudOptimisedDownloadSize("test-uuid", parameters);
        assertEquals("{\"estimated_output_bytes\":123}", result, "Raw das JSON should be returned unchanged");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map<String, String>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<Map<String, String>> uriVarsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForObject(urlCaptor.capture(), entityCaptor.capture(), eq(String.class), uriVarsCaptor.capture());

        assertEquals(HOST + "/api/v1/das/data/{uuid}/estimate_size", urlCaptor.getValue());
        assertEquals("test-uuid", uriVarsCaptor.getValue().get("uuid"));
        assertEquals(parameters, entityCaptor.getValue().getBody(),
                "The batch-style parameter map must be forwarded to DAS unchanged");
    }

    @Test
    public void testEstimateNon2xxPropagates() {
        when(httpClient.postForObject(anyString(), any(), eq(String.class), anyMap()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThrows(HttpClientErrorException.class, () ->
                dasService.estimateCloudOptimisedDownloadSize(
                        "bad-uuid", Map.of("uuid", "bad-uuid", "key", "missing.zarr", "output_format", "netcdf")));
    }

    private record CapturedRequest(String url, Map<String, String> params) {
    }
}
