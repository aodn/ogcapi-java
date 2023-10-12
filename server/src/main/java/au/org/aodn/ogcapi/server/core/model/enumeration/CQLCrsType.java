package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.Arrays;

public enum CQLCrsType {
    CRS84("CRS:84", 4326, "http://www.opengis.net/def/crs/OGC/1.3/CRS84"),
    UNKNOWN(null, null, null);

    public final String code;
    public final String url;
    public final Integer srid;

    CQLCrsType(String code, Integer srid, String url) {
        this.code = code;
        this.url = url;
        this.srid = srid;
    }

    public static CQLCrsType convertFromUrl(String url) {
        return Arrays.stream(CQLCrsType.values())
                .filter(f -> f.equals(url))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
