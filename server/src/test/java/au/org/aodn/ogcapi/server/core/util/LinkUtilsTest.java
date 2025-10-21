package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LinkUtilsTest {
    @BeforeAll
    static void setUp() {
        ConstructUtils.setObjectMapper(new ObjectMapper());
    }

    @Test
    void testParseLinkTitleDescription_normalCase() {
        String[] result = LinkUtils.parseLinkTitleDescription("{\"title\":\"mnf:adcp_public_data\",\"description\":\"ADCP data for Southern Surveyor Voyage SS 09/2003\"}");

        assertEquals("mnf:adcp_public_data", result[0]);
        assertEquals("ADCP data for Southern Surveyor Voyage SS 09/2003", result[1]);
    }

    @Test
    void testParseLinkTitleDescription_emptyDescription() {
        String[] result = LinkUtils.parseLinkTitleDescription("{\"title\":\"Ocean Radar page on IMOS website\",\"description\":\"\"}");;

        assertEquals("Ocean Radar page on IMOS website", result[0]);
        assertNull(result[1]);
    }

    @Test
    void testParseLinkTitleDescription_titleWithBrackets() {
        String[] result = LinkUtils.parseLinkTitleDescription("{\"title\":\"DATA ACCESS - GBR10 benthic habitat type [Geotiff direct download]\",\"description\":\"\"}");

        assertEquals("DATA ACCESS - GBR10 benthic habitat type [Geotiff direct download]", result[0]);
        assertNull(result[1]);
    }

    @Test
    void testParseLinkTitleDescription_emptyTitleAndDescription() {
        String[] result = LinkUtils.parseLinkTitleDescription(null);

        assertNull(result[0]);
        assertNull(result[1]);
    }

    @Test
    void testParseLinkTitleDescription_multipleNestedBrackets() {
        String[] result = LinkUtils.parseLinkTitleDescription("{\"title\":\"Title [level1 [level2]]\",\"description\":\"Final Description\"}");

        assertEquals("Title [level1 [level2]]", result[0]);
        assertEquals("Final Description", result[1]);
    }

    @Test
    void testParseLinkTitleDescription_whitespaceOnlyDescription() {
        String[] result = LinkUtils.parseLinkTitleDescription("{\"title\":\"Title\",\"description\":\"   \"}");

        assertEquals("Title", result[0]);
        assertNull(result[1]);
    }

    @Test
    void testParseLinkTitleDescription_titleWithAbstract() {
        String combinedTitle = "{\"title\":\"Title\",\"resourceAbstract\":\"This is a associated resource.\"}";
        String[] result = LinkUtils.parseLinkTitleDescription(combinedTitle);

        assertEquals(combinedTitle, result[0]);
        assertNull(result[1]);
    }
}
