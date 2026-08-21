package au.org.aodn.ogcapi.server;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTest {

    @Test
    void initSetsUtcAsDefaultTimezone() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"));

            new Server().init();

            assertEquals(ZoneId.of("UTC"), ZoneId.systemDefault());
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }
}
