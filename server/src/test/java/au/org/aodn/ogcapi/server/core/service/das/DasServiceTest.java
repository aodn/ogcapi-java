package au.org.aodn.ogcapi.server.core.service.das;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DasService URL building: null date params are omitted so they never reach URI
 * template expansion, and buoy/mooring ids go as path variables so they are encoded once. The API
 * key is attached by the RestTemplate bean, so ConfigTest covers it, and the streamed size
 * estimate has its own DasServiceEstimateStreamTest.
 */
public class DasServiceTest {

    private static final String HOST = "http://localhost:5001";

    private RestTemplate httpClient;
    private DasService dasService;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        DasProperties config = new DasProperties(
                HOST, null,"test-secret", "",
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(20)
        );

        dasService = new DasService(config, httpClient, mock(WebClient.class), new ObjectMapper());

        when(httpClient.getForEntity(anyString(), eq(byte[].class), anyMap()))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));
        when(httpClient.getForEntity(anyString(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));
    }

    @SuppressWarnings("unchecked")
    private CapturedRequest captureMapRequest() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).getForEntity(urlCaptor.capture(), eq(byte[].class), mapCaptor.capture());
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
        verify(httpClient).getForEntity(urlCaptor.capture(), eq(byte[].class));
        assertEquals(HOST + "/api/v1/das/data/feature-collection/wave-buoy/latest", urlCaptor.getValue());
    }

    private record CapturedRequest(String url, Map<String, String> params) {
    }
}
