package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LinkUtilsTest {
    @Test
    void testParseLinkTitleDescription_normalCase() {
        String[] result = LinkUtils.parseLinkTitleDescription("mnf:adcp_public_data[ADCP data for Southern Surveyor Voyage SS 09/2003]");

        assertEquals("mnf:adcp_public_data", result[0]);
        assertEquals("ADCP data for Southern Surveyor Voyage SS 09/2003", result[1]);
    }

    @Test
    void testParseLinkTitleDescription_emptyDescription() {
        String[] result = LinkUtils.parseLinkTitleDescription("Ocean Radar page on IMOS website[]");

        assertEquals("Ocean Radar page on IMOS website", result[0]);
        assertNull(result[1]);
    }

    @Test
    void testParseLinkTitleDescription_titleWithBrackets() {
        String[] result = LinkUtils.parseLinkTitleDescription("DATA ACCESS - GBR10 benthic habitat type [Geotiff direct download][]");

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
        String[] result = LinkUtils.parseLinkTitleDescription("Title [level1 [level2]] [Final Description]");

        assertEquals("Title [level1 [level2]]", result[0]);
        assertEquals("Final Description", result[1]);
    }

    @Test
    void testParseLinkTitleDescription_whitespaceOnlyDescription() {
        String[] result = LinkUtils.parseLinkTitleDescription("Title[   ]");

        assertEquals("Title", result[0]);
        assertNull(result[1]);
    }
}
