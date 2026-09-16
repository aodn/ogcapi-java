package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.exception.InvalidParameterException;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OGCApiServiceTest {

    /**
     * Verify process function correct, it converts datetime field to CQL filter, here the time isn't important
     * as parser will handle it and error out if date time format is incorrect.
     */
    @Test
    public void verifyProcessDatetimeParameter() {

        String o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(), "../2021-10-10", "");
        assertEquals( "temporal before 2021-10-10", o, "Before incorrect1");

        o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(), "/2021-10-10", "");
        assertEquals( "temporal before 2021-10-10", o, "Before incorrect2");

        o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(),"2021-10-10/", "");
        assertEquals( "temporal after 2021-10-10", o, "After incorrect1");

        o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(),"2021-10-10/..", "");
        assertEquals( "temporal after 2021-10-10", o, "After incorrect1");

        o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(),"2021-10-10/2022-10-10", "");
        assertEquals( "temporal during 2021-10-10/2022-10-10", o, "During incorrect1");

        o = OGCApiService.processDatetimeParameter(CQLFields.temporal.name(),"/2021-10-10", "geometry is null");
        assertEquals( "geometry is null AND temporal before 2021-10-10", o, "Before plus filter incorrect1");
    }

    /**
     * limit becomes a CQL page_size appended last, so it wins over any page_size already in the filter.
     */
    @Test
    public void verifyProcessLimitParameter() {
        String o = OGCApiService.processLimitParameter(null, null);
        assertEquals("page_size=10", o, "Spec default when nothing given");

        o = OGCApiService.processLimitParameter(null, "page_size=3");
        assertEquals("page_size=3", o, "Filter page_size kept when no limit");

        o = OGCApiService.processLimitParameter(null, "temporal after 2021-10-10");
        assertEquals("temporal after 2021-10-10 AND page_size=10", o, "Default appended to filter");

        o = OGCApiService.processLimitParameter(5, null);
        assertEquals("page_size=5", o, "Limit alone");

        o = OGCApiService.processLimitParameter(2, "page_size=3");
        assertEquals("page_size=3 AND page_size=2", o, "Limit appended last so it overrides");

        assertThrows(InvalidParameterException.class, () -> OGCApiService.processLimitParameter(0, null), "Below 1 rejected");
        assertThrows(InvalidParameterException.class, () -> OGCApiService.processLimitParameter(10001, null), "Above max rejected");
    }
}
