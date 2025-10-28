package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.DescribeLayerResponse;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadWfsDataService
 */
@ExtendWith(MockitoExtension.class)
public class DownloadWfsDataServiceTest {

    @Mock
    private WmsServer wmsServer;

    @Mock
    private WfsServer wfsServer;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private WfsDefaultParam wfsDefaultParam;

    private DownloadWfsDataService downloadWfsDataService;

    @BeforeEach
    public void setUp() {
        downloadWfsDataService = new DownloadWfsDataService(
                wmsServer, wfsServer, restTemplate, wfsDefaultParam
        );
    }

    /**
     * Helper method to create a list of downloadable fields for testing
     */
    private List<DownloadableFieldModel> createTestDownloadableFields() {
        List<DownloadableFieldModel> fields = new ArrayList<>();
        
        // Add geometry field
        fields.add(DownloadableFieldModel.builder()
                .name("geom")
                .label("geom")
                .type("geometrypropertytype")
                .build());
        
        // Add datetime field
        fields.add(DownloadableFieldModel.builder()
                .name("timestamp")
                .label("timestamp")
                .type("dateTime")
                .build());
        
        return fields;
    }

    @Test
    public void testPrepareWfsRequestUrl_WithNullDates() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        List<DownloadableFieldModel> fields = createTestDownloadableFields();
        
        DescribeLayerResponse describeLayerResponse = mock(DescribeLayerResponse.class);
        DescribeLayerResponse.LayerDescription layerDescription = mock(DescribeLayerResponse.LayerDescription.class);
        DescribeLayerResponse.Query query = mock(DescribeLayerResponse.Query.class);
        
        when(describeLayerResponse.getLayerDescription()).thenReturn(layerDescription);
        when(layerDescription.getWfs()).thenReturn("https://test.com/geoserver/wfs");
        when(layerDescription.getQuery()).thenReturn(query);
        when(query.getTypeName()).thenReturn(layerName);
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(describeLayerResponse);
        when(wfsServer.getDownloadableFields(eq(uuid), any(FeatureRequest.class), anyString())).thenReturn(fields);
        
        Map<String, String> defaultParams = new HashMap<>();
        defaultParams.put("service", "WFS");
        defaultParams.put("version", "2.0.0");
        defaultParams.put("request", "GetFeature");
        when(wfsDefaultParam.getDownload()).thenReturn(defaultParams);
        
        // Test with null dates (non-specified dates from frontend)
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, null, null, null, null, layerName
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
        List<DownloadableFieldModel> fields = createTestDownloadableFields();
        
        DescribeLayerResponse describeLayerResponse = mock(DescribeLayerResponse.class);
        DescribeLayerResponse.LayerDescription layerDescription = mock(DescribeLayerResponse.LayerDescription.class);
        DescribeLayerResponse.Query query = mock(DescribeLayerResponse.Query.class);
        
        when(describeLayerResponse.getLayerDescription()).thenReturn(layerDescription);
        when(layerDescription.getWfs()).thenReturn("https://test.com/geoserver/wfs");
        when(layerDescription.getQuery()).thenReturn(query);
        when(query.getTypeName()).thenReturn(layerName);
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(describeLayerResponse);
        when(wfsServer.getDownloadableFields(eq(uuid), any(FeatureRequest.class), anyString())).thenReturn(fields);
        
        Map<String, String> defaultParams = new HashMap<>();
        defaultParams.put("service", "WFS");
        defaultParams.put("version", "2.0.0");
        defaultParams.put("request", "GetFeature");
        when(wfsDefaultParam.getDownload()).thenReturn(defaultParams);
        
        // Test with empty string dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, "", "", null, null, layerName
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
        List<DownloadableFieldModel> fields = createTestDownloadableFields();
        
        DescribeLayerResponse describeLayerResponse = mock(DescribeLayerResponse.class);
        DescribeLayerResponse.LayerDescription layerDescription = mock(DescribeLayerResponse.LayerDescription.class);
        DescribeLayerResponse.Query query = mock(DescribeLayerResponse.Query.class);
        
        when(describeLayerResponse.getLayerDescription()).thenReturn(layerDescription);
        when(layerDescription.getWfs()).thenReturn("https://test.com/geoserver/wfs");
        when(layerDescription.getQuery()).thenReturn(query);
        when(query.getTypeName()).thenReturn(layerName);
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(describeLayerResponse);
        when(wfsServer.getDownloadableFields(eq(uuid), any(FeatureRequest.class), anyString())).thenReturn(fields);
        
        Map<String, String> defaultParams = new HashMap<>();
        defaultParams.put("service", "WFS");
        defaultParams.put("version", "2.0.0");
        defaultParams.put("request", "GetFeature");
        when(wfsDefaultParam.getDownload()).thenReturn(defaultParams);
        
        // Test with valid dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName
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
        List<DownloadableFieldModel> fields = createTestDownloadableFields();
        
        DescribeLayerResponse describeLayerResponse = mock(DescribeLayerResponse.class);
        DescribeLayerResponse.LayerDescription layerDescription = mock(DescribeLayerResponse.LayerDescription.class);
        DescribeLayerResponse.Query query = mock(DescribeLayerResponse.Query.class);
        
        when(describeLayerResponse.getLayerDescription()).thenReturn(layerDescription);
        when(layerDescription.getWfs()).thenReturn("https://test.com/geoserver/wfs");
        when(layerDescription.getQuery()).thenReturn(query);
        when(query.getTypeName()).thenReturn(layerName);
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(describeLayerResponse);
        when(wfsServer.getDownloadableFields(eq(uuid), any(FeatureRequest.class), anyString())).thenReturn(fields);
        
        Map<String, String> defaultParams = new HashMap<>();
        defaultParams.put("service", "WFS");
        defaultParams.put("version", "2.0.0");
        defaultParams.put("request", "GetFeature");
        when(wfsDefaultParam.getDownload()).thenReturn(defaultParams);
        
        // Test with only start date (end date is null)
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, null, null, null, layerName
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
        List<DownloadableFieldModel> fields = createTestDownloadableFields();
        
        DescribeLayerResponse describeLayerResponse = mock(DescribeLayerResponse.class);
        DescribeLayerResponse.LayerDescription layerDescription = mock(DescribeLayerResponse.LayerDescription.class);
        DescribeLayerResponse.Query query = mock(DescribeLayerResponse.Query.class);
        
        when(describeLayerResponse.getLayerDescription()).thenReturn(layerDescription);
        when(layerDescription.getWfs()).thenReturn("https://test.com/geoserver/wfs");
        when(layerDescription.getQuery()).thenReturn(query);
        when(query.getTypeName()).thenReturn(layerName);
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(describeLayerResponse);
        when(wfsServer.getDownloadableFields(eq(uuid), any(FeatureRequest.class), anyString())).thenReturn(fields);
        
        Map<String, String> defaultParams = new HashMap<>();
        defaultParams.put("service", "WFS");
        defaultParams.put("version", "2.0.0");
        defaultParams.put("request", "GetFeature");
        when(wfsDefaultParam.getDownload()).thenReturn(defaultParams);
        
        // Test with MM-YYYY format dates
        String result = downloadWfsDataService.prepareWfsRequestUrl(
                uuid, startDate, endDate, null, null, layerName
        );
        
        // Verify URL contains temporal filter with converted dates
        assertNotNull(result);
        assertTrue(result.contains("typeName=" + layerName));
        assertTrue(result.contains("cql_filter"), "URL should contain cql_filter");
        assertTrue(result.contains("DURING"), "CQL filter should contain DURING operator");
        // Start date should be first day of January 2023
        assertTrue(result.contains("2023-01-01T00:00:00Z"), "Start date should be converted to first day of month");
        // End date should be last day of December 2023
        assertTrue(result.contains("2023-12-31T23:59:59Z"), "End date should be converted to last day of month");
    }

    @Test
    public void testPrepareWfsRequestUrl_NoWfsServerUrl() {
        // Setup
        String uuid = "test-uuid";
        String layerName = "test:layer";
        
        when(wmsServer.describeLayer(eq(uuid), any(FeatureRequest.class))).thenReturn(null);
        when(wfsServer.getFeatureServerUrl(eq(uuid), eq(layerName))).thenReturn(java.util.Optional.empty());
        
        // Test with no WFS server URL available
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            downloadWfsDataService.prepareWfsRequestUrl(
                    uuid, null, null, null, null, layerName
            );
        });
        
        assertTrue(exception.getMessage().contains("No WFS server URL found"));
    }
}

