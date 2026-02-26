package au.org.aodn.ogcapi.server.core.service.geoserver.wps;

import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WpsServerTest {

    protected Logger log = LoggerFactory.getLogger(WpsServerTest.class);

    @Mock
    protected WmsServer wmsServer;

    @Mock
    protected WfsServer wfsServer;

    @Mock
    protected RestTemplate restTemplate;

    protected HttpEntity<?> entity = HttpEntity.EMPTY;
    protected WpsServer wpsServer;

    @BeforeEach
    void setUp() {
        wpsServer = new WpsServer(wmsServer, wfsServer, restTemplate, entity);
    }

    @Test
    void testCreateEstimateDownloadSizeXmlRequest() throws Exception {
        // 1. Create a Filter for the test (state = 'TAS')
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.equal(ff.property("state"), ff.literal("TAS"), true);

        String layerName = "aodn:my_layer";

        // 2. Generate the XML
        String resultXml = wpsServer.createEstimateDownloadSizeXmlRequest(layerName, filter);

        // 4. Assertions
        assertNotNull(resultXml);

        // Verify Identifier for the process
        assertTrue(resultXml.contains("<ows:Identifier>gs:DownloadEstimator</ows:Identifier>"),
                "Missing process identifier");

        // Verify LayerName input
        assertTrue(resultXml.contains("<ows:Identifier>layername</ows:Identifier>"),
                "Missing layername identifier");
        assertTrue(resultXml.contains("<wps:LiteralData>aodn:my_layer</wps:LiteralData>"),
                "Missing layername value");

        // Verify Filter input and CDATA wrapping
        assertTrue(resultXml.contains("<ows:Identifier>filter</ows:Identifier>"),
                "Missing filter identifier");

        // Check for CDATA and specific filter content
        assertTrue(resultXml.contains("<![CDATA["), "Output should contain CDATA section");
        assertTrue(resultXml.contains("ogc:PropertyIsEqualTo"), "CDATA should contain encoded filter");
        assertTrue(resultXml.contains("TAS"), "CDATA should contain filter literal value");
    }

    @Test
    void getEstimateDownloadSize_success_returnsBody() throws Exception {
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String wfsUrl = "http://example.com/geoserver/wfs";
        AtomicReference<String> xml = new AtomicReference<>();

        WpsServer.WpsProcessRequest request = new WpsServer.WpsProcessRequest();
        request.setLayerName(layerName);

        when(wfsServer.getFeatureServerUrl(uuid, layerName)).thenReturn(Optional.of(wfsUrl));
        when(wfsServer.buildCqlFilter(eq(uuid), any())).thenReturn("state = 'TAS'");

        String mockResponseBody = "{\"estimate\": \"12345 bytes\"}";

        when(restTemplate.exchange(
                eq("http://example.com/geoserver/wps"),
                eq(HttpMethod.POST),
                argThat(e -> {
                    xml.set(e.getBody().toString());
                    return e.getBody().toString().contains("gs:DownloadEstimator") &&
                                e.getBody().toString().contains("filter");
                }),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(mockResponseBody, HttpStatus.OK));

        String result = wpsServer.getEstimateDownloadSize(uuid, request);

        Diff diff = DiffBuilder
                .compare(
                        """
                        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                        <wps:Execute
                            xmlns:wps="http://www.opengis.net/wps/1.0.0"
                            xmlns:ows="http://www.opengis.net/ows/1.1"
                            xmlns:xlink="http://www.w3.org/1999/xlink"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema" service="WPS" version="1.0.0">
                            <ows:Identifier>gs:DownloadEstimator</ows:Identifier>
                            <wps:DataInputs>
                                <wps:Input>
                                    <ows:Identifier>layername</ows:Identifier>
                                    <wps:Data>
                                        <wps:LiteralData>test:layer</wps:LiteralData>
                                    </wps:Data>
                                </wps:Input>
                                <wps:Input>
                                    <ows:Identifier>filter</ows:Identifier>
                                    <wps:Data>
                                        <wps:ComplexData mimeType="text/xml"><![CDATA[<?xml version="1.0" encoding="UTF-8"?><ogc:Filter xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc"><ogc:PropertyIsEqualTo matchCase="true"><ogc:PropertyName>state</ogc:PropertyName><ogc:Literal>TAS</ogc:Literal></ogc:PropertyIsEqualTo></ogc:Filter>]]></wps:ComplexData>
                                    </wps:Data>
                                </wps:Input>
                            </wps:DataInputs>
                            <wps:ResponseForm>
                                <wps:RawDataOutput mimeType="application/json">
                                    <ows:Identifier>result</ows:Identifier>
                                </wps:RawDataOutput>
                            </wps:ResponseForm>
                        </wps:Execute>
                        """
                )
                .withTest(xml.get())
                .ignoreWhitespace()
                .normalizeWhitespace()
                .ignoreComments()
                .checkForSimilar()
                .build();

        assertEquals(mockResponseBody, result);
        assertFalse(diff.hasDifferences(), "XML body match");
        verify(restTemplate).exchange(eq("http://example.com/geoserver/wps"), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void getEstimateDownloadSize_noWfsUrl_throwsException() {
        String uuid = "test-uuid";
        WpsServer.WpsProcessRequest request = new WpsServer.WpsProcessRequest();
        request.setLayerName("test:layer");

        when(wfsServer.getFeatureServerUrl(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> wpsServer.getEstimateDownloadSize(uuid, request));
    }

    @Test
    void getEstimateDownloadSize_non2xx_returnsEmpty() throws Exception {
        String uuid = "test-uuid";
        String layerName = "test:layer";
        String wfsUrl = "http://example.com/geoserver/wfs";

        WpsServer.WpsProcessRequest request = new WpsServer.WpsProcessRequest();
        request.setLayerName(layerName);

        when(wfsServer.getFeatureServerUrl(uuid, layerName)).thenReturn(Optional.of(wfsUrl));
        when(wfsServer.buildCqlFilter(eq(uuid), any())).thenReturn("state = 'TAS'");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.BAD_REQUEST));

        String result = wpsServer.getEstimateDownloadSize(uuid, request);

        assertEquals("", result);
    }
}
