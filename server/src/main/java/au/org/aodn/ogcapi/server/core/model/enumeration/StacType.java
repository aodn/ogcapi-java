package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacType {
    Collection("Collection");

    public final String value;
    public static final String field = "type";

    StacType(String s) {value = s;}
}
