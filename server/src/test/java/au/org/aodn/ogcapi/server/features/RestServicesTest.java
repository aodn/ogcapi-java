package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.DasService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class RestServicesTest {

    @Mock
    private DasService dasService;

    @Mock
    private WmsServer wmsServer;

    @Mock
    private WfsServer wfsServer;

    @InjectMocks
    private RestServices restServices;

    private AutoCloseable closeableMock;

    private static final String SUPPORTED_COLLECTION_ID = "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc";

    @BeforeEach
    public void setUp() {
        closeableMock = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanUp() throws Exception {
        closeableMock.close();
    }

    @Test
    public void testGetWaveBuoysLatestDateSuccess() {
        byte[] mockResponse = "{\"latest_date\":\"2024-01-01\"}".getBytes();
        when(dasService.isCollectionSupported(SUPPORTED_COLLECTION_ID)).thenReturn(true);
        when(dasService.getWaveBuoysLatestDate()).thenReturn(mockResponse);

        ResponseEntity<?> response = restServices.getWaveBuoysLatestDate(SUPPORTED_COLLECTION_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    public void testGetWaveBuoysLatestDateUnsupportedCollection() {
        when(dasService.isCollectionSupported("unsupported-id")).thenReturn(false);

        ResponseEntity<?> response = restServices.getWaveBuoysLatestDate("unsupported-id");

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
    }

    @Test
    public void testGetWaveBuoysLatestDateServiceError() {
        when(dasService.isCollectionSupported(SUPPORTED_COLLECTION_ID)).thenReturn(true);
        when(dasService.getWaveBuoysLatestDate()).thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getWaveBuoysLatestDate(SUPPORTED_COLLECTION_ID);

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

        ResponseEntity<?> response = restServices.getWfsFieldValue("any-works", FeatureRequest.builder().build());

    }
}
