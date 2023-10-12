package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacSummeries {
    Geometry("summaries.proj:geometry");

    public final String field;

    StacSummeries(String s) { field = s;}
}
