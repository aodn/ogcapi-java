package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacBasicField {
    UUID("id", "id"),
    Title("title", "title"),
    Description("description", "description"),
    Providers("providers", "providers.name");

    public final String searchField;    // Field in STAC object
    public final String displayField;   // Field that is named externally

    StacBasicField(String displayField, String searchField) {
        this.displayField = displayField;
        this.searchField = searchField;
    }
}
