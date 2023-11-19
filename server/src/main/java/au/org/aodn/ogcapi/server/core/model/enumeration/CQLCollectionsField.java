package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 */
public enum CQLCollectionsField {
    geometry(StacSummeries.Geometry.searchField, StacSummeries.Geometry.displayField),
    temporal(StacSummeries.Temporal.searchField, StacSummeries.Temporal.displayField),
    title(StacTitle.searchField, StacTitle.displayField),
    description(StacDescription.searchField, StacDescription.displayField),
    id(StacUUID.UUID.searchField, StacUUID.UUID.displayField);

    protected final String searchField;
    protected final String displayField;

    CQLCollectionsField(String field, String displayField) {
        this.searchField = field;
        this.displayField = displayField;
    }

    public String getSearchField() {
        return this.searchField;
    }
    public String getDisplayField() {
        return this.displayField;
    }

}
