package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.common.RestService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServiceTest {

    @Test
    public void verifyConformation() {
        RestService s = new RestService();
        assertEquals(List.of("http://www.opengis.net/spec/ogcapi-common-1/1.0/conf/core"), s.getConformanceDeclaration(), "Confirmation Declaration Incorrect");
    }
}
