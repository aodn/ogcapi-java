package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DasServiceTest {

    @Mock
    private RestTemplate httpClient;

    private DasService dasService;

    @BeforeEach
    public void setUp() {
        DASConfig dasConfig = new DASConfig();
        dasConfig.host = "http://das-test-host";
        dasConfig.secret = "test-secret";

        dasService = new DasService();
        dasService.dasConfig = dasConfig;
        dasService.httpClient = httpClient;
    }

    private HttpEntity<Map<String, Object>> callAndCaptureEntity(
            String uuid, List<String> keys, String startDate, String endDate,
            Object multiPolygon, List<String> columns, String outputFormat) {

        when(httpClient.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class), anyMap()))
                .thenReturn(ResponseEntity.ok("{\"estimated_output_bytes\":123}"));

        String result = dasService.estimateCloudOptimisedDownloadSize(
                uuid, keys, startDate, endDate, multiPolygon, columns, outputFormat);
        assertEquals("{\"estimated_output_bytes\":123}", result, "Raw das JSON should be returned unchanged");

        var entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var uriVarsCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(httpClient).exchange(urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class), uriVarsCaptor.capture());

        assertEquals("http://das-test-host/api/v1/das/data/{uuid}/estimate_size", urlCaptor.getValue());
        assertEquals(uuid, uriVarsCaptor.getValue().get("uuid"));

        return (HttpEntity<Map<String, Object>>) entityCaptor.getValue();
    }

    @Test
    public void testEstimatePostsJsonBodyWithApiKey() {
        HttpEntity<Map<String, Object>> entity = callAndCaptureEntity(
                "test-uuid", List.of("a.zarr", "b.zarr"), "2023-01-01", "2023-01-31",
                "non-specified", null, "netcdf");

        HttpHeaders headers = entity.getHeaders();
        assertEquals("test-secret", headers.getFirst("X-API-KEY"));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));

        Map<String, Object> body = entity.getBody();
        assertNotNull(body);
        assertEquals(List.of("a.zarr", "b.zarr"), body.get("keys"));
        assertEquals("2023-01-01", body.get("start_date"));
        assertEquals("2023-01-31", body.get("end_date"));
        assertEquals("netcdf", body.get("output_format"));
        assertEquals("non-specified", body.get("multi_polygon"));
        assertFalse(body.containsKey("columns"), "columns must be omitted when not provided");
    }

    @Test
    public void testEstimateNullDatesBecomeNonSpecifiedAndOptionalFieldsOmitted() {
        HttpEntity<Map<String, Object>> entity = callAndCaptureEntity(
                "test-uuid", null, null, null, null, null, "csv");

        Map<String, Object> body = entity.getBody();
        assertNotNull(body);
        assertNull(body.get("keys"), "null keys means all keys of the uuid");
        assertEquals("non-specified", body.get("start_date"));
        assertEquals("non-specified", body.get("end_date"));
        assertEquals("csv", body.get("output_format"));
        assertFalse(body.containsKey("multi_polygon"), "multi_polygon must be omitted when null");
        assertFalse(body.containsKey("columns"), "columns must be omitted when null");
    }

    @Test
    public void testEstimateNon2xxPropagates() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class), anyMap()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThrows(HttpClientErrorException.class, () ->
                dasService.estimateCloudOptimisedDownloadSize(
                        "bad-uuid", List.of("missing.zarr"), null, null, null, null, "netcdf"));
    }
}
