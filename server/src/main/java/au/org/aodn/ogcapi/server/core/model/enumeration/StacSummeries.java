package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.List;

public enum StacSummeries {
    Score("summaries.score", "summaries.score", "summaries.score", null),
    Geometry("summaries.proj:geometry","extent.bbox"),
    GeometryNoLand("summaries.proj:geometry_noland","summaries.proj:geometry_noland"),
    TemporalStart("summaries.temporal.start", ""),
    TemporalEnd("summaries.temporal.end", ""),
    Temporal("summaries.temporal", "extent.temporal", "summaries.temporal", List.of(TemporalStart, TemporalEnd)),
    UpdateFrequency("summaries.update_frequency", "summaries.update_frequency"),
    DatasetProvider("summaries.dataset_provider", "summaries.dataset_provider"),
    DatasetGroup("summaries.dataset_group", "summaries.dataset_group"),
    Status("summaries.status", "summaries.status"),
    Statement("summaries.statement", "summaries.statement"),
    Credits("summaries.credits", "summaries.credits"),
    Scope("summaries.scope", "summaries.scope")
    ;

    public final String sortField;
    public final String searchField;
    public final String displayField;
    public final List<StacSummeries> subfields;

    StacSummeries(String search, String display) { this(search, display, null); }

    StacSummeries(String search, String display, List<StacSummeries> f) {
        this(search, display, search, f);
    }

    StacSummeries(String search, String display, String order, List<StacSummeries> f) {
        searchField = search;
        displayField = display;
        sortField = order;
        subfields = f;
    }
}
