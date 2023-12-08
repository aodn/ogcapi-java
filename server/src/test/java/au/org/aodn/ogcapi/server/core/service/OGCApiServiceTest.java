package au.org.aodn.ogcapi.server.core.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OGCApiServiceTest {

    // The class is abstract, so need to implement it before we can test it.
    class OGCApiServiceImpl extends OGCApiService {
        @Override
        public List<String> getConformanceDeclaration() {
            return null;
        }
    };
    /**
     * Verify process function correct, it converts datetime field to CQL filter, here the time isn't important
     * as parser will handle it and error out if date time format is incorrect.
     */
    @Test
    public void verifyProcessDatetimeParameter() {
        OGCApiServiceImpl impl = new OGCApiServiceImpl();

        String o = impl.processDatetimeParameter("../2021-10-10", "");
        assertEquals( "temporal before 2021-10-10", o, "Before incorrect1");

        o = impl.processDatetimeParameter("/2021-10-10", "");
        assertEquals( "temporal before 2021-10-10", o, "Before incorrect2");

        o = impl.processDatetimeParameter("2021-10-10/", "");
        assertEquals( "temporal after 2021-10-10", o, "After incorrect1");

        o = impl.processDatetimeParameter("2021-10-10/..", "");
        assertEquals( "temporal after 2021-10-10", o, "After incorrect1");

        o = impl.processDatetimeParameter("2021-10-10/2022-10-10", "");
        assertEquals( "temporal during 2021-10-10/2022-10-10", o, "During incorrect1");

        o = impl.processDatetimeParameter("/2021-10-10", "geometry is null");
        assertEquals( "geometry is null AND temporal before 2021-10-10", o, "Before plus filter incorrect1");
    }
}
