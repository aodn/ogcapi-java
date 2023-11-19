package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.List;

public enum StacSummeries {
    Geometry("summaries.proj:geometry","extent.bbox"),
    TemporalStart("summaries.temporal.start", ""),
    TemporalEnd("summaries.temporal.end", ""),
    Temporal("summaries.temporal", "extent.temporal", List.of(TemporalStart, TemporalEnd));

    public final String searchField;
    public final String displayField;
    public final List<StacSummeries> subfields;

    StacSummeries(String s, String d) { this(s,d, null); }

    StacSummeries(String s, String d, List<StacSummeries> f) {
        searchField = s;
        displayField = d;
        subfields = f;
    }
}
