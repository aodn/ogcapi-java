package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
}
