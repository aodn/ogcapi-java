package au.org.aodn.ogcapi.server.features;

import au.org.aodn.ogcapi.server.core.model.enumeration.DasSiteProduct;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.service.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

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

    private static HttpClientErrorException dasBadGateway(byte[] errorBody) {
        HttpHeaders dasErrorHeaders = new HttpHeaders();
        dasErrorHeaders.set("Content-Type", "application/json");
        return HttpClientErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", dasErrorHeaders, errorBody, null);
    }

    // ----- sites between dates -----

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesSuccess(DasSiteProduct product) {
        byte[] body = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getSitesBetweenDates(product, VALID_START, VALID_END)).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, VALID_START, VALID_END);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesForwardsDasHeaders(DasSiteProduct product) {
        // Headers set by DAS (e.g. caching/rate-limit headers) must reach the client, not just the body.
        byte[] body = "{\"type\":\"FeatureCollection\"}".getBytes();
        ResponseEntity<byte[]> dasResponse = ResponseEntity.ok().header("X-Custom-Header", "das-value").body(body);
        when(dasService.getSitesBetweenDates(product, VALID_START, VALID_END)).thenReturn(dasResponse);

        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, VALID_START, VALID_END);

        assertEquals("das-value", response.getHeaders().getFirst("X-Custom-Header"));
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesNullDatesPassThrough(DasSiteProduct product) {
        // Both dates null is allowed; the service is still called (with nulls) and the result returned
        byte[] body = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getSitesBetweenDates(product, null, null)).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dasService).getSitesBetweenDates(product, null, null);
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesNonUtcRejected(DasSiteProduct product) {
        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, "2024-01-01T00:00:00+10:00", VALID_END);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getSitesBetweenDates(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesMalformedRejected(DasSiteProduct product) {
        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, "not-a-date", VALID_END);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getSitesBetweenDates(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesStartAfterEndRejected(DasSiteProduct product) {
        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, VALID_END, VALID_START);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getSitesBetweenDates(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesServiceError(DasSiteProduct product) {
        when(dasService.getSitesBetweenDates(product, VALID_START, VALID_END))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, VALID_START, VALID_END);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSitesBetweenDatesDasErrorIsForwardedNotMaskedAs500(DasSiteProduct product) {
        // DAS itself returning e.g. a 404/502 must reach the client as that status with its
        // body, not get collapsed into a generic 500 by the catch-all error handler.
        byte[] errorBody = "{\"message\":\"upstream unavailable\"}".getBytes();
        when(dasService.getSitesBetweenDates(product, VALID_START, VALID_END)).thenThrow(dasBadGateway(errorBody));

        ResponseEntity<?> response = restServices.getSitesBetweenDates(product, VALID_START, VALID_END);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertArrayEquals(errorBody, (byte[]) response.getBody());
    }

    // ----- latest available date -----

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetLatestAvailableDateSuccess(DasSiteProduct product) {
        byte[] body = "{\"latest_date\":\"2024-01-01\"}".getBytes();
        when(dasService.getLatestAvailableDate(product)).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<?> response = restServices.getLatestAvailableDate(product);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetLatestAvailableDateServiceError(DasSiteProduct product) {
        when(dasService.getLatestAvailableDate(product)).thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getLatestAvailableDate(product);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetLatestAvailableDateDasErrorIsForwarded(DasSiteProduct product) {
        byte[] errorBody = "{\"message\":\"upstream unavailable\"}".getBytes();
        when(dasService.getLatestAvailableDate(product)).thenThrow(dasBadGateway(errorBody));

        ResponseEntity<?> response = restServices.getLatestAvailableDate(product);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertArrayEquals(errorBody, (byte[]) response.getBody());
    }

    // ----- site details between dates -----

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSiteDetailsBetweenDatesSuccess(DasSiteProduct product) {
        byte[] body = "{\"type\":\"FeatureCollection\"}".getBytes();
        when(dasService.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1")).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }

    @ParameterizedTest
    @CsvSource({
            "WAVE_BUOY, waveBuoy",
            "MOORING, mooring",
            "MOORING_ESTUARINE_WATER_QUALITY, mooring"
    })
    public void testGetSiteDetailsBetweenDatesNullSiteRejected(DasSiteProduct product, String parameter) {
        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Parameter '" + parameter + "' is required", response.getBody());
        verify(dasService, never()).getSiteDetailsBetweenDates(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSiteDetailsBetweenDatesInvalidDateRejected(DasSiteProduct product) {
        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, "not-a-date", VALID_END, "SITE-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getSiteDetailsBetweenDates(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSiteDetailsBetweenDatesStartAfterEndRejected(DasSiteProduct product) {
        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, VALID_END, VALID_START, "SITE-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasService, never()).getSiteDetailsBetweenDates(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSiteDetailsBetweenDatesServiceError(DasSiteProduct product) {
        when(dasService.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @ParameterizedTest
    @EnumSource(DasSiteProduct.class)
    public void testGetSiteDetailsBetweenDatesDasErrorIsForwarded(DasSiteProduct product) {
        byte[] errorBody = "{\"message\":\"upstream unavailable\"}".getBytes();
        when(dasService.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1")).thenThrow(dasBadGateway(errorBody));

        ResponseEntity<?> response = restServices.getSiteDetailsBetweenDates(product, VALID_START, VALID_END, "SITE-1");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertArrayEquals(errorBody, (byte[]) response.getBody());
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
}
