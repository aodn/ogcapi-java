package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacBasicField {
    UUID("id", "id", "id.keyword"),
    Title("title", "title", "title.keyword"),
    Description("description", "description"),
    Providers(
            "providers",    // This result in the whole provider section return
            "providers.name"
    ),
    ParameterVocabs(
            "parameter_vocabs",
            "summaries.parameter_vocabs"
    ),
    PlatformVocabs(
            "platform_vocabs",
            "summaries.platform_vocabs"
    ),
    OrganisationVocabs(
            "organisation_vocabs",
            "summaries.organisation_vocabs"
    ),
    Links("links", "links"),
    Collection("collection", "collection", "collection.keyword"),
    AssetsSummary("assets.summary", "assets.summary")
    ;

    // Field that use to do sort, elastic search treat FieldData (searchField) differently, a searchField is not
    // efficient for sorting.
    public final String sortField;
    public final String searchField;    // Field in STAC object
    public final String displayField;   // Field that is named externally

    StacBasicField(String displayField, String searchField) {
        this(displayField, searchField, searchField);
    }

    StacBasicField(String displayField, String searchField, String sortField) {
        this.displayField = displayField;
        this.searchField = searchField;
        this.sortField = sortField;
    }
}
