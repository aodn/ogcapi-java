package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EmailUtilsTest {

    /**
     * Test reading image that doesn't exist
     */
    @Test
    void testReadImageNotFound() {
        assertThrows(IOException.class, () -> {
            EmailUtils.readBase64Image("fake-file.txt");
        });
    }

    /**
     * Test the polygon icon resource exists and is a PNG data URL
     */
    @Test
    void testPolygonImageResourceExists() throws IOException {
        String dataUrl = EmailUtils.readBase64Image("polygon.txt");

        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        assertTrue(dataUrl.length() > "data:image/png;base64,".length());
    }

    /**
     * Test single bbox with no number
     */
    @Test
    void testSingleBbox() {
        String html = EmailUtils.buildBboxSection("-40.0", "-41.0", "145.0", "146.0", 0);

        assertTrue(html.contains("Bounding Box Selection"));
        assertTrue(html.contains("N: -40.0"));
        assertTrue(html.contains("S: -41.0"));
        assertTrue(html.contains("W: 145.0"));
        assertTrue(html.contains("E: 146.0"));
    }

    /**
     * Test bbox coordinate order groups latitude bounds then longitude bounds (N, S, W, E)
     */
    @Test
    void testBboxCoordinateOrder() {
        String html = EmailUtils.buildBboxSection("-40.0", "-41.0", "145.0", "146.0", 0);

        int n = html.indexOf("N: -40.0");
        int s = html.indexOf("S: -41.0");
        int w = html.indexOf("W: 145.0");
        int e = html.indexOf("E: 146.0");

        assertTrue(n < s && s < w && w < e, "Expected order N, S, W, E");
    }

    /**
     * Test multiple bboxes with numbers
     */
    @Test
    void testMultipleBboxes() {
        String html1 = EmailUtils.buildBboxSection("-40.0", "-41.0", "145.0", "146.0", 1);
        String html2 = EmailUtils.buildBboxSection("-38.0", "-39.0", "147.0", "148.0", 2);

        assertTrue(html1.contains("Bounding Box 1"));
        assertTrue(html2.contains("Bounding Box 2"));
    }

    /**
     * Test decimal formatting - now tests that original string values are preserved
     */
    @Test
    void testDecimalFormat() {
        String html = EmailUtils.buildBboxSection("-35.12345", "-36.54321", "150.11111", "151.99999", 0);

        assertTrue(html.contains("N: -35.12345"));
        assertTrue(html.contains("S: -36.54321"));
        assertTrue(html.contains("W: 150.11111"));
        assertTrue(html.contains("E: 151.99999"));
    }

    /**
     * Test scientific notation values are preserved
     */
    @Test
    void testScientificNotation() {
        String html = EmailUtils.buildBboxSection("4.9E-324", "-11.919807423710694", "-45.42305428582753", "4.9E-324", 0);

        assertTrue(html.contains("N: 4.9E-324"));
        assertTrue(html.contains("S: -11.919807423710694"));
        assertTrue(html.contains("W: -45.42305428582753"));
        assertTrue(html.contains("E: 4.9E-324"));
    }

    /**
     * Test image placeholder exists
     */
    @Test
    void testImagePlaceholder() {
        String html = EmailUtils.buildBboxSection("-40.0", "-41.0", "145.0", "146.0", 0);

        assertTrue(html.contains("{{BBOX_IMG}}"));
    }

    /**
     * Test that full world bbox is treated as empty
     */
    @Test
    void testFullWorldBboxReturnsEmpty() {
        Map<String, Object> fullWorld = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(
                                List.of(
                                        List.of(-180, 90),
                                        List.of(-180, -90),
                                        List.of(180, -90),
                                        List.of(180, 90),
                                        List.of(-180, 90)
                                )
                        )
                )
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", fullWorld, new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that subsetting section is hidden when no date and no bbox
     */
    @Test
    void testNoSubsettingReturnsEmpty() {
        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", null, new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that "non-specified" string is treated as empty
     */
    @Test
    void testNonSpecifiedStringReturnsEmpty() {
        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", "non-specified", new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that empty coordinates list is treated as empty
     */
    @Test
    void testEmptyCoordinatesReturnsEmpty() {
        Map<String, Object> emptyCoords = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of()
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", emptyCoords, new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that invalid multipolygon structure is treated as empty
     */
    @Test
    void testInvalidMultipolygonReturnsEmpty() {
        Map<String, Object> invalid = Map.of(
                "type", "MultiPolygon"
                // Missing coordinates field
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", invalid, new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that valid bbox with dates shows subsetting section
     */
    @Test
    void testValidBboxWithDatesShowsSection() {
        Map<String, Object> validBbox = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(
                                List.of(
                                        List.of(145, -40),
                                        List.of(145, -41),
                                        List.of(146, -41),
                                        List.of(146, -40),
                                        List.of(145, -40)
                                )
                        )
                )
        );

        String result = EmailUtils.generateSubsettingSection(
                "2024-01-01", "2024-12-31", validBbox, new ObjectMapper()
        );

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Subsetting for this collection"));
        assertTrue(result.contains("Bounding Box"));
        assertTrue(result.contains("Time Range"));
    }

    /**
     * Test that only dates (no bbox) shows subsetting section
     */
    @Test
    void testOnlyDatesShowsSection() {
        String result = EmailUtils.generateSubsettingSection(
                "2024-01-01", "2024-12-31", null, new ObjectMapper()
        );

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Subsetting for this collection"));
        assertTrue(result.contains("Time Range"));
        assertFalse(result.contains("Bounding Box"));
    }

    /**
     * Test that dates are shown in the Australian format (dd/MM/yyyy), matching the frontend
     */
    @Test
    void testDatesUseAustralianFormat() {
        String result = EmailUtils.generateSubsettingSection(
                "2024-01-05", "2024-12-31", null, new ObjectMapper()
        );

        assertTrue(result.contains("05/01/2024 - 31/12/2024"), "Expected dd/MM/yyyy Australian date format");
    }

    /**
     * Test that the frontend default full range (1970-01-01 to today) is treated as no subsetting and hidden
     */
    @Test
    void testDefaultFullRangeHidesSection() {
        String today = LocalDate.now().toString();

        String result = EmailUtils.generateSubsettingSection(
                "1970-01-01", today, null, new ObjectMapper()
        );

        assertEquals("", result);
    }

    /**
     * Test that a default lower bound with a real upper bound still shows the time range
     */
    @Test
    void testDefaultLowerBoundWithRealEndShowsSection() {
        String result = EmailUtils.generateSubsettingSection(
                "1970-01-01", "2024-12-31", null, new ObjectMapper()
        );

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Time Range"));
        assertTrue(result.contains("01/01/1970 - 31/12/2024"));
    }

    /**
     * Test that only bbox (no dates) shows subsetting section
     */
    @Test
    void testOnlyBboxShowsSection() {
        Map<String, Object> validBbox = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(
                                List.of(
                                        List.of(145, -40),
                                        List.of(145, -41),
                                        List.of(146, -41),
                                        List.of(146, -40),
                                        List.of(145, -40)
                                )
                        )
                )
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", validBbox, new ObjectMapper()
        );

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Subsetting for this collection"));
        assertTrue(result.contains("Bounding Box"));
        assertFalse(result.contains("Time Range"));
    }

    /**
     * Test single polygon with no number
     */
    @Test
    void testSinglePolygon() {
        List<List<BigDecimal>> vertices = List.of(
                List.of(new BigDecimal("145.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.5"), new BigDecimal("-41.0")),
                List.of(new BigDecimal("145.5"), new BigDecimal("-42.0")),
                List.of(new BigDecimal("144.5"), new BigDecimal("-41.0"))
        );

        String html = EmailUtils.buildPolygonSection(vertices, 0);

        assertTrue(html.contains("Polygon Selection"));
        assertTrue(html.contains("Point 1: (-40.0, 145.0)"));
        assertTrue(html.contains("Point 5: (-41.0, 144.5)"));
    }

    /**
     * Test multiple polygons with numbers
     */
    @Test
    void testMultiplePolygons() {
        List<List<BigDecimal>> vertices = List.of(
                List.of(new BigDecimal("145.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.5"), new BigDecimal("-41.0")),
                List.of(new BigDecimal("145.5"), new BigDecimal("-42.0")),
                List.of(new BigDecimal("144.5"), new BigDecimal("-41.0"))
        );

        String html1 = EmailUtils.buildPolygonSection(vertices, 1);
        String html2 = EmailUtils.buildPolygonSection(vertices, 2);

        assertTrue(html1.contains("Polygon Selection 1"));
        assertTrue(html2.contains("Polygon Selection 2"));
    }

    /**
     * Test polygon vertices preserve full precision (no scientific notation)
     */
    @Test
    void testPolygonDecimalFormat() {
        List<List<BigDecimal>> vertices = List.of(
                List.of(new BigDecimal("150.11111"), new BigDecimal("-35.12345")),
                List.of(new BigDecimal("151.99999"), new BigDecimal("-36.54321")),
                List.of(new BigDecimal("152.50000"), new BigDecimal("-37.00001")),
                List.of(new BigDecimal("151.00000"), new BigDecimal("-38.00002")),
                List.of(new BigDecimal("150.00000"), new BigDecimal("-37.00003"))
        );

        String html = EmailUtils.buildPolygonSection(vertices, 0);

        assertTrue(html.contains("Point 1: (-35.12345, 150.11111)"));
        assertTrue(html.contains("Point 2: (-36.54321, 151.99999)"));
    }

    /**
     * Test polygon uses its own image placeholder
     */
    @Test
    void testPolygonImagePlaceholder() {
        List<List<BigDecimal>> vertices = List.of(
                List.of(new BigDecimal("145.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.0"), new BigDecimal("-40.0")),
                List.of(new BigDecimal("146.5"), new BigDecimal("-41.0")),
                List.of(new BigDecimal("145.5"), new BigDecimal("-42.0")),
                List.of(new BigDecimal("144.5"), new BigDecimal("-41.0"))
        );

        String html = EmailUtils.buildPolygonSection(vertices, 0);

        assertTrue(html.contains("{{POLYGON_IMG}}"));
        assertFalse(html.contains("{{BBOX_IMG}}"));
    }

    /**
     * Test that a freeform polygon (>4 vertices) shows the polygon section, not a bbox
     */
    @Test
    void testFreeformPolygonShowsPolygonSection() {
        Map<String, Object> polygon = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(
                                List.of(
                                        List.of(145.0, -40.0),
                                        List.of(146.0, -40.0),
                                        List.of(146.5, -41.0),
                                        List.of(145.5, -42.0),
                                        List.of(144.5, -41.0),
                                        List.of(145.0, -40.0)
                                )
                        )
                )
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", polygon, new ObjectMapper()
        );

        assertFalse(result.isEmpty());
        assertTrue(result.contains("Subsetting for this collection"));
        assertTrue(result.contains("Polygon Selection"));
        assertFalse(result.contains("Bounding Box"));
    }

    /**
     * Test that a rectangle (4 vertices) shows the bbox section, not a polygon
     */
    @Test
    void testBboxNotRenderedAsPolygon() {
        Map<String, Object> validBbox = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(
                                List.of(
                                        List.of(145, -40),
                                        List.of(145, -41),
                                        List.of(146, -41),
                                        List.of(146, -40),
                                        List.of(145, -40)
                                )
                        )
                )
        );

        String result = EmailUtils.generateSubsettingSection(
                "non-specified", "non-specified", validBbox, new ObjectMapper()
        );

        assertTrue(result.contains("Bounding Box"));
        assertFalse(result.contains("Polygon Selection"));
    }
}
