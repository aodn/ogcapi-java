package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacDescription {
    Description("description");

    public final String value;
    public static final String searchField = "description";
    public static final String displayField = "description";

    StacDescription(String s) {value = s;}
}
