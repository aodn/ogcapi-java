package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.List;

public enum StacSummeries {
    Score("summaries.score", "summaries.score"),
    Geometry("summaries.proj:geometry","extent.bbox"),
    TemporalStart("summaries.temporal.start", ""),
    TemporalEnd("summaries.temporal.end", ""),
    Temporal("summaries.temporal", "extent.temporal", List.of(TemporalStart, TemporalEnd)),
    UpdateFrequency("summaries.update_frequency", "summaries.update_frequency"),
    DatasetProvider("summaries.dataset_provider", "summaries.dataset_provider"),
    DatasetGroup("summaries.dataset_group", "summaries.dataset_group");

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
