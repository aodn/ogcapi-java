package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.model.enumeration.DasSiteProduct;
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
 * template expansion, and product/site ids go as path variables so they are encoded once. The API
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
        dasService.getSitesBetweenDates(DasSiteProduct.WAVE_BUOY, "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z");

        CapturedRequest captured = captureMapRequest();
        assertTrue(captured.url.startsWith(HOST + "/api/v1/das/data/feature-collection/{product}"));
        assertEquals("wave-buoy", captured.params.get("product"));
        assertTrue(captured.url.contains("start_date={start_date}"));
        assertTrue(captured.url.contains("end_date={end_date}"));
        assertEquals("2024-01-01T00:00:00Z", captured.params.get("start_date"));
        assertEquals("2024-01-02T00:00:00Z", captured.params.get("end_date"));
    }

    @Test
    public void testBetweenDatesOmitsNullStart() {
        dasService.getSitesBetweenDates(DasSiteProduct.WAVE_BUOY, null, "2024-01-02T00:00:00Z");

        CapturedRequest captured = captureMapRequest();
        assertFalse(captured.url.contains("start_date"), "null start_date must not appear in URL");
        assertFalse(captured.params.containsKey("start_date"), "null start_date must not be a uri variable");
        assertTrue(captured.url.contains("end_date={end_date}"));
        assertEquals("2024-01-02T00:00:00Z", captured.params.get("end_date"));
    }

    @Test
    public void testBetweenDatesOmitsBothNullDates() {
        dasService.getSitesBetweenDates(DasSiteProduct.WAVE_BUOY, null, null);

        CapturedRequest captured = captureMapRequest();
        // No query string at all when both dates are absent
        assertFalse(captured.url.contains("?"), "no query params expected, got: " + captured.url);
        assertEquals(Map.of("product", "wave-buoy"), captured.params);
    }

    @Test
    public void testSiteDetailsSendsRawSiteAsPathVariable() {
        // A site name with a space must be passed raw; RestTemplate encodes the path variable once.
        dasService.getSiteDetailsBetweenDates(DasSiteProduct.WAVE_BUOY, "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", "Apollo Bay");

        CapturedRequest captured = captureMapRequest();
        assertTrue(captured.url.contains("/{product}/{site}"), "site must be a path variable, got: " + captured.url);
        assertEquals("Apollo Bay", captured.params.get("site"));
        assertEquals("wave-buoy", captured.params.get("product"));
    }

    @Test
    public void testLatestAvailableDateSendsNoDates() {
        dasService.getLatestAvailableDate(DasSiteProduct.WAVE_BUOY);

        CapturedRequest captured = captureMapRequest();
        assertEquals(HOST + "/api/v1/das/data/feature-collection/{product}/latest", captured.url);
        assertEquals(Map.of("product", "wave-buoy"), captured.params);
    }

    private record CapturedRequest(String url, Map<String, String> params) {
    }
}
