package au.org.aodn.ogcapi.server.core.service.wps;

import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.Test;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WpsServerTest {
    @Test
    void testCreateEstimateDownloadSize() throws Exception {
        WpsServer wpsServer = new WpsServer();

        // 1. Create a Filter for the test (state = 'TAS')
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.equal(ff.property("state"), ff.literal("TAS"), true);

        String layerName = "aodn:my_layer";

        // 2. Generate the XML
        String resultXml = wpsServer.createEstimateDownloadSize(layerName, filter);

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
}
