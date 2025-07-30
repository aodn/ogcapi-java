package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException;
import au.org.aodn.ogcapi.server.core.configuration.WfsServerConfig;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DownloadableFieldsServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private WfsServerConfig wfsServerConfig;

    @InjectMocks
    private DownloadableFieldsService downloadableFieldsService;

    @InjectMocks
    private RestApi restApi;

    private static final String AUTHORIZED_SERVER = "https://geoserver-123.aodn.org.au/geoserver/wfs";
    private static final String UNAUTHORIZED_SERVER = "https://unauthorized-server.com/wfs";


    @Test
    public void testGetDownloadableFieldsSuccess() {
        // Mock successful WFS response with geometry and datetime fields
        String mockWfsResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                        xmlns:gml="http://www.opengis.net/gml/3.2"
                        xmlns:test="test.namespace"
                        targetNamespace="test.namespace">
                <xsd:complexType name="testLayerType">
                    <xsd:complexContent>
                        <xsd:extension base="gml:AbstractFeatureType">
                            <xsd:sequence>
                                <xsd:element name="geom" type="gml:GeometryPropertyType"/>
                                <xsd:element name="timestamp" type="xsd:dateTime"/>
                                <xsd:element name="name" type="xsd:string"/>
                            </xsd:sequence>
                        </xsd:extension>
                    </xsd:complexContent>
                </xsd:complexType>
            </xsd:schema>
            """;
        when(wfsServerConfig.validateAndGetApprovedServerUrl(AUTHORIZED_SERVER)).thenReturn(AUTHORIZED_SERVER);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockWfsResponse, HttpStatus.OK));

        List<DownloadableFieldModel> result = downloadableFieldsService.getDownloadableFields(AUTHORIZED_SERVER, "test:layer");

        assertNotNull(result);
        assertEquals(2, result.size());

        // Check geometry field
        DownloadableFieldModel geomField = result.stream()
                .filter(f -> "geom".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(geomField);
        assertEquals("geom", geomField.getLabel());
        assertEquals("geometrypropertytype", geomField.getType());

        // Check datetime field
        DownloadableFieldModel timeField = result.stream()
                .filter(f -> "timestamp".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(timeField);
        assertEquals("timestamp", timeField.getLabel());
        assertEquals("dateTime", timeField.getType());
    }

    @Test
    public void testGetDownloadableFieldsEmptyResponse() {
        // Mock WFS response with no geometry or datetime fields
        String mockWfsResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                        xmlns:gml="http://www.opengis.net/gml/3.2"
                        xmlns:test="test.namespace"
                        targetNamespace="test.namespace">
                <xsd:complexType name="testLayerType">
                    <xsd:complexContent>
                        <xsd:extension base="gml:AbstractFeatureType">
                            <xsd:sequence>
                                <xsd:element name="name" type="xsd:string"/>
                                <xsd:element name="id" type="xsd:int"/>
                            </xsd:sequence>
                        </xsd:extension>
                    </xsd:complexContent>
                </xsd:complexType>
            </xsd:schema>
            """;
        when(wfsServerConfig.validateAndGetApprovedServerUrl(AUTHORIZED_SERVER)).thenReturn(AUTHORIZED_SERVER);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockWfsResponse, HttpStatus.OK));

        DownloadableFieldsNotFoundException exception = assertThrows(
                DownloadableFieldsNotFoundException.class,
                () -> downloadableFieldsService.getDownloadableFields(AUTHORIZED_SERVER, "test:layer")
        );

        assertTrue(exception.getMessage().contains("No downloadable fields found"));
        assertTrue(exception.getMessage().contains("test:layer"));
    }

    @Test
    public void testGetDownloadableFieldsUnauthorizedServer() {
        when(wfsServerConfig.validateAndGetApprovedServerUrl(UNAUTHORIZED_SERVER))
                .thenThrow(new UnauthorizedServerException("Access to WFS server '" + UNAUTHORIZED_SERVER + "' is not authorized"));
        UnauthorizedServerException exception = assertThrows(
                UnauthorizedServerException.class,
                () -> downloadableFieldsService.getDownloadableFields(UNAUTHORIZED_SERVER, "test:layer")
        );

        assertTrue(exception.getMessage().contains("not authorized"));
        assertTrue(exception.getMessage().contains(UNAUTHORIZED_SERVER));
    }

    @Test
    public void testGetDownloadableFieldsWfsError() {
        when(wfsServerConfig.validateAndGetApprovedServerUrl(AUTHORIZED_SERVER)).thenReturn(AUTHORIZED_SERVER);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        DownloadableFieldsNotFoundException exception = assertThrows(
                DownloadableFieldsNotFoundException.class,
                () -> downloadableFieldsService.getDownloadableFields(AUTHORIZED_SERVER, "invalid:layer")
        );

        assertTrue(exception.getMessage().contains("No downloadable fields found"));
    }

    @Test
    public void testGetDownloadableFieldsNetworkError() {
        when(wfsServerConfig.validateAndGetApprovedServerUrl(AUTHORIZED_SERVER)).thenReturn(AUTHORIZED_SERVER);
        // Mock network error
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        DownloadableFieldsNotFoundException exception = assertThrows(
                DownloadableFieldsNotFoundException.class,
                () -> downloadableFieldsService.getDownloadableFields(AUTHORIZED_SERVER, "test:layer")
        );

        assertTrue(exception.getMessage().contains("No downloadable fields found"));
    }

    @Test
    public void testRestApiDownloadableFieldsMissingServerUrl() {
        // Test with missing serverUrl parameter - should return 400
        ResponseEntity<?> response = restApi.getFeature(
                "test-collection",
                "downloadableFields",
                null, // properties
                null, // bbox
                null, // datetime
                null, // serverUrl - missing
                "test:layer" // layerName
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "Should return 400 for missing serverUrl");
    }

    @Test
    public void testRestApiDownloadableFieldsMissingLayerName() {
        // Test with missing layerName parameter - should return 400
        ResponseEntity<?> response = restApi.getFeature(
                "test-collection",
                "downloadableFields",
                null, // properties
                null, // bbox
                null, // datetime
                "https://test.com/wfs", // serverUrl
                null // layerName - missing
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "Should return 400 for missing layerName");
    }
}
