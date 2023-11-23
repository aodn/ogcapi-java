package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.ArrayList;
import java.util.List;

public enum StacSummeries {
    Score("summaries.score"),
    Geometry("summaries.proj:geometry"),
    TemporalStart("summaries.temporal.start"),
    TemporalEnd("summaries.temporal.end"),
    Temporal("summaries.temporal", List.of(TemporalStart, TemporalEnd));

    public final String field;
    public final List<StacSummeries> subfields;

    StacSummeries(String s) { this(s, null); }

    StacSummeries(String s, List<StacSummeries> f) {
        field = s;
        subfields = f;
    }
}
