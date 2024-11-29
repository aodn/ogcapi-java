package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.service.OGCApiService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RestApiTest {
    @Test
    public void verifyProcessDatetimeParameter() {
        String filter = OGCApiService.processDatetimeParameter("../2020-12-30", null);
        Assertions.assertEquals(filter, "temporal before 2020-12-30");

        filter = OGCApiService.processDatetimeParameter("/2020-12-30", null);
        Assertions.assertEquals(filter, "temporal before 2020-12-30");

        filter = OGCApiService.processDatetimeParameter("2020-12-30/..", null);
        Assertions.assertEquals(filter, "temporal after 2020-12-30");

        filter = OGCApiService.processDatetimeParameter("2020-12-30/", null);
        Assertions.assertEquals(filter, "temporal after 2020-12-30");

        filter = OGCApiService.processDatetimeParameter("2020-12-30/2020-12-31", null);
        Assertions.assertEquals(filter, "temporal during 2020-12-30/2020-12-31");

        filter = OGCApiService.processDatetimeParameter("2020-12-30/2020-12-31", "");
        Assertions.assertEquals(filter, "temporal during 2020-12-30/2020-12-31");

        filter = OGCApiService.processDatetimeParameter("2020-12-30/2020-12-31", "TEST");
        Assertions.assertEquals(filter, "TEST AND temporal during 2020-12-30/2020-12-31");
    }
    /**
     * Check the cql generate correctly where given the value from ogc api q parameter, we need to search
     * a few more attributes other than title and description
     */
    @Test
    public void verifyProcessQueryParameter() {
        String filter = OGCApiService.processQueryParameter(List.of("temperature"), null);
        Assertions.assertEquals(
                "(organisation_vocabs LIKE '%temperature%' OR organisation_vocabs IS NULL OR parameter_vocabs LIKE '%temperature%' OR parameter_vocabs IS NULL OR platform_vocabs LIKE '%temperature%' OR platform_vocabs IS NULL)",
                filter
        );

        filter = OGCApiService.processQueryParameter(List.of("temperature"), "filter");
        Assertions.assertEquals(
                "(organisation_vocabs LIKE '%temperature%' OR organisation_vocabs IS NULL OR parameter_vocabs LIKE '%temperature%' OR parameter_vocabs IS NULL OR platform_vocabs LIKE '%temperature%' OR platform_vocabs IS NULL) AND filter",
                filter
        );

        filter = OGCApiService.processQueryParameter(List.of("temperature", "sea water"), "filter");
        Assertions.assertEquals(
                "(organisation_vocabs LIKE '%temperature%' OR organisation_vocabs IS NULL OR parameter_vocabs LIKE '%temperature%' OR parameter_vocabs IS NULL OR platform_vocabs LIKE '%temperature%' OR platform_vocabs IS NULL OR organisation_vocabs LIKE '%sea water%' OR organisation_vocabs IS NULL OR parameter_vocabs LIKE '%sea water%' OR parameter_vocabs IS NULL OR platform_vocabs LIKE '%sea water%' OR platform_vocabs IS NULL) AND filter",
                filter
        );
    }
}
