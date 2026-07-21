package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import org.springframework.http.HttpHeaders;

public class DasUtils {

    private DasUtils() {
    }

    public static HttpHeaders authHeaders(DasProperties dasProperties) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", dasProperties.secret());
        if (dasProperties.internal() != null) {
            headers.set("x-internal-das-header-secret", dasProperties.internal());
        }
        return headers;
    }
}
