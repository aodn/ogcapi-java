package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadWfsDataService
 */
@SpringBootTest(classes = {WfsDefaultParam.class})
@TestPropertySource(locations = "classpath:application.yaml")
@EnableConfigurationProperties(WfsDefaultParam.class)
@ExtendWith(MockitoExtension.class)
public class DownloadWfsDataServiceTest {

    @Mock
    private RestTemplateUtils restTemplateUtils;

    @Mock
    private HttpEntity<?> pretendUserEntity;

    @Autowired
    WfsDefaultParam wfsDefaultParam;

    @Spy
    RestTemplate restTemplate;

    DownloadWfsDataService downloadWfsDataService;
    WmsServer wmsServer;
    WfsServer wfsServer;

    @BeforeEach
    public void setUp() {

        Search search = Mockito.mock(Search.class);

        wfsServer = Mockito.spy(new WfsServer(search, restTemplate, restTemplateUtils, pretendUserEntity, wfsDefaultParam));
        wfsServer.self = wfsServer;

        wmsServer = Mockito.spy(new WmsServer(search, wfsServer, pretendUserEntity));

        downloadWfsDataService = new DownloadWfsDataService(
                wfsServer, restTemplate, pretendUserEntity, 16384, new ObjectMapper()
        );
    }

    /**
     * Helper method to create a WFSFieldModel for testing
     */
    private WfsFields createTestWFSFieldModel() {
        List<WfsField> fields = new ArrayList<>();

        // Add geometry field
        fields.add(WfsField.builder()
                .name("geom")
                .label("geom")
                .type("geometrypropertytype")
                .build());

        // Add datetime field
        fields.add(WfsField.builder()
                .name("timestamp")
                .label("timestamp")
                .type("dateTime")
                .build());

        return WfsFields.builder()
                .typename("testLayer")
                .fields(fields)
                .build();
    }

    @Test
    public void testPrepareWfsRequestUrl_WithNullDates() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with null dates (non-specified dates from frontend)
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, null, null, null, null, layerName, null, -1L, false
        );

        // Verify URL doesn't contain temporal filter when dates are null
        assertNotNull(result);
        assertTrue(result.contains("typeName=" + layerName));
        assertFalse(result.contains("cql_filter"), "URL should not contain cql_filter when dates are null");
    }

    @Test
    public void testPrepareWfsRequestUrl_WithEmptyDates() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with empty string dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, "", "", null, null, layerName, null, -1L, false
        );

        // Verify URL doesn't contain temporal filter when dates are empty
        assertNotNull(result);
        assertTrue(result.contains("typeName=" + layerName));
        assertFalse(result.contains("cql_filter"), "URL should not contain cql_filter when dates are empty");
    }

    @Test
    public void testPrepareWfsRequestUrl_WithValidDates() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String startDate = "2023-01-01";
        String endDate = "2023-12-31";
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with valid dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName, null, -1L, false
        );

        // Verify URL contains temporal filter when valid dates are provided
        assertNotNull(result);
        assertTrue(result.contains("typeName=" + layerName));
        assertTrue(result.contains("cql_filter"), "URL should contain cql_filter with valid dates");
        assertTrue(result.contains("DURING"), "CQL filter should contain DURING operator");
        assertTrue(result.contains("2023-01-01T00:00:00Z"), "CQL filter should contain start date");
        assertTrue(result.contains("2023-12-31T23:59:59Z"), "CQL filter should contain end date");
    }

    @Test
    public void testPrepareWfsRequestUrl_WithOnlyStartDate() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String startDate = "2023-01-01";
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with only start date (end date is null)
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, null, null, null, layerName, null, -1L, false
        );

        // Verify URL doesn't contain temporal filter when only one date is provided
        assertNotNull(result);
        assertTrue(result.contains("typeName=" + layerName));
        assertFalse(result.contains("cql_filter"), "URL should not contain cql_filter when only start date is provided");
    }

    @Test
    public void testPrepareWfsRequestUrl_WithMMYYYYFormat() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String startDate = "01-2023";  // MM-YYYY format
        String endDate = "12-2023";    // MM-YYYY format
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with MM-YYYY format dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName, null, -1L, false
        );

        // Verify URL contains temporal filter with converted dates
        assertNotNull(result);
        assertEquals(
                "https://test.com/geoserver/wfs?VERSION=1.0.0&typeName=test:layer&SERVICE=WFS&REQUEST=GetFeature&outputFormat=text/csv&cql_filter=((timestamp DURING 2023-01-01T00:00:00Z/2023-12-31T23:59:59Z))",
                result
        );
    }

    @Test
    public void testPrepareWfsRequestUrl_NoWfsServerUrl() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";

        doReturn(Optional.empty())
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));

        // Test with no WFS server URL available
        Exception exception = assertThrows(IllegalArgumentException.class, () -> downloadWfsDataService.prepareWfsRequestUrl(
                uuid, null, null, null, null, layerName, null, -1L, false
        ));

        assertTrue(exception.getMessage().contains("No WFS server URL found"));
    }

    @Test
    public void verifyRequestUrlGenerateCorrect() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String startDate = "01-2024";  // MM-YYYY format
        String endDate = "12-2024";    // MM-YYYY format
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        // Test with MM-YYYY format dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName, null, -1L, false
        );

        assertEquals("https://test.com/geoserver/wfs?VERSION=1.0.0&typeName=test:layer&SERVICE=WFS&REQUEST=GetFeature&outputFormat=text/csv&cql_filter=((timestamp DURING 2024-01-01T00:00:00Z/2024-12-31T23:59:59Z))", result, "Correct url 1");

        result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName, "shape-zip", -1L, false
        );
        assertEquals("https://test.com/geoserver/wfs?VERSION=1.0.0&typeName=test:layer&SERVICE=WFS&REQUEST=GetFeature&outputFormat=shape-zip&cql_filter=((timestamp DURING 2024-01-01T00:00:00Z/2024-12-31T23:59:59Z))", result, "Correct url 1");
    }
    /**
     * Make sure the url generated contains the correct polygon
     *
     * @throws JsonProcessingException - Not expected
     */
    @Test
    public void verifyRequestUrlGenerateCorrectWithPolygon() throws JsonProcessingException {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String startDate = "01-2024";  // MM-YYYY format
        String endDate = "12-2024";    // MM-YYYY format
        WfsFields wfsFieldModel = createTestWFSFieldModel();

        doReturn(Optional.of("https://test.com/geoserver/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), eq(layerName));
        doReturn(wfsFieldModel)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        Object multiPolygon = new ObjectMapper().readValue(
                "{ \"type\": \"MultiPolygon\", \"coordinates\": [[[[112.01192842942288, -22.393450547704845], [129.68986083498982, -22.393450547704845], [129.68986083498982, -12.647778557898718], [112.01192842942288, -12.647778557898718], [112.01192842942288, -22.393450547704845]]], [[[128.29423459244452, 3.5283082597303377], [143.95626242544682, 3.5283082597303377], [143.95626242544682, 13.182067934641196], [128.29423459244452, 13.182067934641196], [128.29423459244452, 3.5283082597303377]]]] }",
                HashMap.class
        );

        // Test with MM-YYYY format dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, multiPolygon, null, layerName, null, -1L, false
        );

        assertEquals(
                "https://test.com/geoserver/wfs?VERSION=1.0.0&typeName=test:layer&SERVICE=WFS&REQUEST=GetFeature&outputFormat=text/csv&cql_filter=((timestamp DURING 2024-01-01T00:00:00Z/2024-12-31T23:59:59Z)) AND INTERSECTS(geom,MULTIPOLYGON (((112.01192842942288 -22.393450547704845, 129.68986083498982 -22.393450547704845, 129.68986083498982 -12.647778557898718, 112.01192842942288 -12.647778557898718, 112.01192842942288 -22.393450547704845)), ((128.29423459244452 3.5283082597303377, 143.95626242544682 3.5283082597303377, 143.95626242544682 13.182067934641196, 128.29423459244452 13.182067934641196, 128.29423459244452 3.5283082597303377))))",
                result,
                "Correct url 1");
    }
    /**
     * Verify estimate size on success request
     */
    @Test
    void shouldReturnEstimatedSizeWhenBothRequestsSucceed() {
        String uuid = "lyr-123";
        String layer = "water_bodies";
        String start = "2024-01-01";
        String end = "2024-12-31";
        Object multiPolygon = new Object(); // or real geometry
        List<String> fields = List.of("name", "area");
        String format = "application/json";

        // 1. Count response: GeoJSON with totalFeatures (1 record requested, but totalFeatures = full count)
        String countJson = "{\"totalFeatures\": 227193, \"features\": []}";
        ResponseEntity<String> countResponse = new ResponseEntity<>(countJson, HttpStatus.OK);

        // 2. Sample response (small payload in requested format)
        byte[] sampleBytes = "fake data".getBytes();
        ResponseEntity<byte[]> sampleResponse = new ResponseEntity<>(sampleBytes, HttpStatus.OK);

        doReturn(countResponse)
                .when(restTemplate).exchange(
                    argThat((String url) -> url != null && url.contains("maxFeatures=1")),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class));

        doReturn(sampleResponse)
                .when(restTemplate).exchange(
                    argThat((String url) -> url != null && url.contains("maxFeatures=" + DownloadWfsDataService.SAMPLES_SIZE)),
                    eq(HttpMethod.GET), any(), eq(byte[].class));

        doReturn(Optional.of("http://dummy.com/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), anyString());

        WfsFields fs = WfsFields.builder()
                .fields(List.of(
                        WfsField.builder().type("dateTime").name("time").build()
                ))
                .build();

        doReturn(fs)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        BigInteger size = downloadWfsDataService.estimateDownloadSize(
                uuid, layer, start, end, multiPolygon, fields, format);

        // Should call with maxFeatures=1 to get totalFeatures count via GeoJSON
        verify(restTemplate).exchange(
                argThat((String url) -> url != null && url.contains("maxFeatures=1") && url.contains("outputFormat=application")),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        );

        // Should also call with maxFeatures=500 to sample bytes for size interpolation
        verify(restTemplate).exchange(
                argThat((String url) -> url != null && url.contains("maxFeatures=" + DownloadWfsDataService.SAMPLES_SIZE)),
                eq(HttpMethod.GET),
                any(),
                eq(byte[].class)
        );

        // totalFeatures=227193, sampleBytes=9 bytes, SAMPLES_SIZE=500
        long expected = 227193L * sampleBytes.length / DownloadWfsDataService.SAMPLES_SIZE;
        assertEquals(BigInteger.valueOf(expected), size, "Size match");
    }
    /**
     * Expect RuntimeException when GeoServer returns JSON without the totalFeatures field
     * (e.g. GeoServer returned an error JSON or unexpected structure)
     */
    @Test
    void throwsExceptionWhenCountResponseMissesTotalFeatures() {

        String uuid = "lyr-123";
        String layer = "imos:aatams_sattag_dm_profile_map1";
        String start = "2024-01-01";
        String end = "2024-12-31";
        Object multiPolygon = new Object();
        List<String> fields = List.of("name", "area");
        String format = "application/json";

        // GeoServer returns JSON but without the totalFeatures field
        String countJson = "{\"error\": \"Feature type unknown\"}";
        ResponseEntity<String> countResponse = new ResponseEntity<>(countJson, HttpStatus.OK);

        doReturn(countResponse)
                .when(restTemplate).exchange(
                        argThat((String url) -> url != null && url.contains("maxFeatures=1")),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(String.class));

        doReturn(Optional.of("http://dummy.com/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), anyString());

        WfsFields fs = WfsFields.builder()
                .fields(List.of(
                        WfsField.builder().type("dateTime").name("time").build()
                ))
                .build();

        doReturn(fs)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        assertThrows(RuntimeException.class,
                () -> downloadWfsDataService.estimateDownloadSize(
                        uuid, layer, start, end, multiPolygon, fields, format),
                "Should throw RuntimeException when totalFeatures is missing from count response");
    }

    @Test
    void returnsNullWhenParserThrowsException() {
        String uuid = "lyr-123";
        String layer = "imos:aatams_sattag_dm_profile_map1";
        String start = "2024-01-01";
        String end = "2024-12-31";
        Object multiPolygon = new Object();
        List<String> fields = List.of("name", "area");
        String format = "application/json";

        // GeoServer returns malformed JSON — Jackson will throw an IOException, expect null back
        String malformedJson = "not-valid-json{{{{";
        ResponseEntity<String> countResponse = new ResponseEntity<>(malformedJson, HttpStatus.OK);

        doReturn(countResponse)
                .when(restTemplate).exchange(
                        argThat((String url) -> url != null && url.contains("maxFeatures=1")),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(String.class));

        doReturn(Optional.of("http://dummy.com/wfs"))
                .when(wfsServer).getFeatureServerUrl(eq(uuid), anyString());

        WfsFields fs = WfsFields.builder()
                .fields(List.of(
                        WfsField.builder().type("dateTime").name("time").build()
                ))
                .build();

        doReturn(fs)
                .when(wfsServer).getDownloadableFields(eq(uuid), any(WfsServer.WfsFeatureRequest.class));

        BigInteger size = downloadWfsDataService.estimateDownloadSize(
                        uuid, layer, start, end, multiPolygon, fields, format);

        assertNull(size, "Size should be null when JSON parsing fails");
    }
}
