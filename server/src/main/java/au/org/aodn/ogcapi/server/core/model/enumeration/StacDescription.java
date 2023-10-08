package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacDescription {
    Title("description");

    public final String value;
    public static final String field = "description";

    StacDescription(String s) {value = s;}
}
