package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum CQLFilterType {
    CQL("cql-text"),
    UNKNOWN("");

    private String lang;

    CQLFilterType(String lang) {
        this.lang = lang;
    }

    public static CQLFilterType convert(String l) {
        for(CQLFilterType v : CQLFilterType.values()) {
            if(v.lang.equals(l)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
