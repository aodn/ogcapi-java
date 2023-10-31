package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacExtentTemporal {
    StacExtentTemporal("extent.temporal");

    public final String value;
    public static final String field = "extent.temporal";
    public static final String path = "extent";

    StacExtentTemporal(String s) {value = s;}
}
