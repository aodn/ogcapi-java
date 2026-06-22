package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.DasService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.service.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class RestServicesTest {

    @Mock
    private DasService dasService;

    @Mock
    private WmsServer wmsServer;

    @Mock
    private WfsServer wfsServer;

    @Mock
    private Search search;

    @InjectMocks
    private RestServices restServices;

    private AutoCloseable closeableMock;

    @BeforeEach
    public void setUp() {
        closeableMock = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanUp() throws Exception {
        closeableMock.close();
    }

    private static final String VALID_START = "2024-01-01T00:00:00Z";
    private static final String VALID_END = "2024-01-02T00:00:00Z";

    // ----- wave_buoys_between_dates -----

    @Test
    public void testGetWaveBuoysBetweenDatesSuccess() {
        byte[] mockResponse = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getWaveBuoysBetweenDates(VALID_START, VALID_END)).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates(VALID_START, VALID_END);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetWaveBuoysBetweenDatesNullDatesPassThrough() {
        // Both dates null is allowed; the service is still called (with nulls) and the result returned
        byte[] mockResponse = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getWaveBuoysBetweenDates(null, null)).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dasService).getWaveBuoysBetweenDates(null, null);
    }

    @Test
    public void testGetWaveBuoysBetweenDatesNonUtcRejected() {
        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates("2024-01-01T00:00:00+10:00", VALID_END);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getWaveBuoysBetweenDates(anyString(), anyString());
    }

    @Test
    public void testGetWaveBuoysBetweenDatesMalformedRejected() {
        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates("not-a-date", VALID_END);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getWaveBuoysBetweenDates(anyString(), anyString());
    }

    @Test
    public void testGetWaveBuoysBetweenDatesStartAfterEndRejected() {
        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates(VALID_END, VALID_START);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getWaveBuoysBetweenDates(anyString(), anyString());
    }

    @Test
    public void testGetWaveBuoysBetweenDatesServiceError() {
        when(dasService.getWaveBuoysBetweenDates(VALID_START, VALID_END))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getWaveBuoysBetweenDates(VALID_START, VALID_END);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----- wave_buoys_latest_available_date -----

    @Test
    public void testGetWaveBuoysLatestAvailableDateSuccess() {
        byte[] mockResponse = "{\"latest_date\":\"2024-01-01\"}".getBytes();
        when(dasService.getWaveBuoysLatestAvailableDate()).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getWaveBuoysLatestAvailableDate();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetWaveBuoysLatestAvailableDateServiceError() {
        when(dasService.getWaveBuoysLatestAvailableDate()).thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getWaveBuoysLatestAvailableDate();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----- wave_buoy_details_between_dates -----

    @Test
    public void testGetWaveBuoyDetailsBetweenDatesSuccess() {
        byte[] mockResponse = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getWaveBuoyDetailsBetweenDates(VALID_START, VALID_END, "BUOY-1")).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getWaveBuoyDetailsBetweenDates(VALID_START, VALID_END, "BUOY-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetWaveBuoyDetailsBetweenDatesNullBuoyRejected() {
        ResponseEntity<?> response = restServices.getWaveBuoyDetailsBetweenDates(VALID_START, VALID_END, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getWaveBuoyDetailsBetweenDates(anyString(), anyString(), any());
    }

    @Test
    public void testGetWaveBuoyDetailsBetweenDatesInvalidDateRejected() {
        ResponseEntity<?> response = restServices.getWaveBuoyDetailsBetweenDates("not-a-date", VALID_END, "BUOY-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getWaveBuoyDetailsBetweenDates(anyString(), anyString(), any());
    }

    @Test
    public void testGetWaveBuoyDetailsBetweenDatesServiceError() {
        when(dasService.getWaveBuoyDetailsBetweenDates(VALID_START, VALID_END, "BUOY-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getWaveBuoyDetailsBetweenDates(VALID_START, VALID_END, "BUOY-1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----- moorings_between_dates -----

    @Test
    public void testGetMooringsBetweenDatesSuccess() {
        byte[] mockResponse = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getMooringsBetweenDates(VALID_START, VALID_END)).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getMooringsBetweenDates(VALID_START, VALID_END);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetMooringsBetweenDatesStartAfterEndRejected() {
        ResponseEntity<?> response = restServices.getMooringsBetweenDates(VALID_END, VALID_START);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getMooringsBetweenDates(anyString(), anyString());
    }

    @Test
    public void testGetMooringsBetweenDatesServiceError() {
        when(dasService.getMooringsBetweenDates(VALID_START, VALID_END))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getMooringsBetweenDates(VALID_START, VALID_END);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----- moorings_latest_available_date -----

    @Test
    public void testGetMooringsLatestAvailableDateSuccess() {
        byte[] mockResponse = "{\"latest_date\":\"2024-01-01\"}".getBytes();
        when(dasService.getMooringsLatestAvailableDate()).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getMooringsLatestAvailableDate();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetMooringsLatestAvailableDateServiceError() {
        when(dasService.getMooringsLatestAvailableDate()).thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getMooringsLatestAvailableDate();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ----- mooring_details_between_dates -----

    @Test
    public void testGetMooringDetailsBetweenDatesSuccess() {
        byte[] mockResponse = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getMooringDetailsBetweenDates(VALID_START, VALID_END, "MOORING-1")).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getMooringDetailsBetweenDates(VALID_START, VALID_END, "MOORING-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetMooringDetailsBetweenDatesNullMooringRejected() {
        ResponseEntity<?> response = restServices.getMooringDetailsBetweenDates(VALID_START, VALID_END, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getMooringDetailsBetweenDates(anyString(), anyString(), any());
    }

    @Test
    public void testGetMooringDetailsBetweenDatesServiceError() {
        when(dasService.getMooringDetailsBetweenDates(VALID_START, VALID_END, "MOORING-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getMooringDetailsBetweenDates(VALID_START, VALID_END, "MOORING-1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void testGetWfsTimeFieldWorks() {
        when(wfsServer.getFieldValues(anyString(), any(WfsServer.WfsFeatureRequest.class), any(ParameterizedTypeReference.class)))
                .thenReturn(
                        """
                            {
                              "type": "FeatureCollection",
                              "features": [
                                {
                                  "type": "Feature",
                                  "id": "srs_ghrsst_l3s_M_1d_ngt_url.fid-4218f2fa_19c6cde1def_1ef0",
                                  "geometry": null,
                                  "properties": {
                                    "time": "2023-11-26T15:20:00Z"
                                  }
                                },
                                {
                                  "type": "Feature",
                                  "id": "srs_ghrsst_l3s_M_1d_ngt_url.fid-4218f2fa_19c6cde1def_1ef2",
                                  "geometry": null,
                                  "properties": {
                                    "time": "2023-11-25T15:20:00Z"
                                  }
                                }
                              ]
                            }
                            """
                );

        when(wfsServer.getWFSFields(anyString(), any(WfsServer.WfsFeatureRequest.class)))
                .thenReturn(List.of(WfsFields.builder()
                        .fields(List.of(
                                WfsField.builder()
                                        .name("TIME")
                                        .build()
                            )
                        )
                        .build()
                ));

        ResponseEntity<?> response = restServices.getWfsFieldValue(
                "any-works",
                FeatureRequest.builder()
                        .properties(List.of("time"))
                        .build()
        );
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> v = (Map<String, List<Object>>)response.getBody();

        assertTrue(v.containsKey("time"), "time field found");
        assertEquals("2023-11-26T15:20:00Z", v.get("time").get(0));
        assertEquals("2023-11-25T15:20:00Z", v.get("time").get(1));

        // It works even property is null
        response = restServices.getWfsFieldValue(
                "any-works",
                FeatureRequest.builder()
                        .build()
        );
        assertInstanceOf(Map.class, response.getBody());

    }

    @Test
    public void testGetFeatureSummaryReturnsEmptyCollectionForAmsaWithoutSearching() throws Exception {
        ResponseEntity<FeatureCollectionGeoJSON> response = restServices.getFeature(
                "2a5739e7-0cb8-444a-b83b-b2bc841b0ce8",
                FeatureId.summary,
                null,
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(FeatureCollectionGeoJSON.TypeEnum.FEATURECOLLECTION, response.getBody().getType());
        assertTrue(response.getBody().getFeatures().isEmpty());

        verify(search, never()).searchFeatureSummary(anyString(), any(), any());
    }
}
