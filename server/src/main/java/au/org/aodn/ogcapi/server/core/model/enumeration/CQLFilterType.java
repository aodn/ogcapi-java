package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.Arrays;

public enum CQLFilterType {
    CQL("cql-text"),
    UNKNOWN(null);

    private String lang;

    CQLFilterType(String lang) {
        this.lang = lang;
    }

    public static CQLFilterType convert(String l) {
        return Arrays.stream(CQLFilterType.values())
                .filter(f -> f.lang.equals(l))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
