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
            "parameter_vocabs", // This result in the whole themes section return
            "summaries.parameter_vocabs"
    ),
    Links("links", "links")
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
