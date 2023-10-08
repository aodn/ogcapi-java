package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacTitle {
    Title("title");

    public final String value;
    public static final String field = "title";

    StacTitle(String s) {value = s;}
}
