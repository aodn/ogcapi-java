package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 */
public enum CQLCollectionsField {
    dataset_provider(StacSummeries.Provider.searchField, StacSummeries.Provider.displayField),
    update_frequency(StacSummeries.UpdateFrequency.searchField, StacSummeries.UpdateFrequency.displayField),
    geometry(StacSummeries.Geometry.searchField, StacSummeries.Geometry.displayField),
    temporal(StacSummeries.Temporal.searchField, StacSummeries.Temporal.displayField),
    title(StacBasicField.Title.searchField, StacBasicField.Title.displayField),
    description(StacBasicField.Description.searchField, StacBasicField.Description.displayField),
    providers(StacBasicField.Providers.searchField, StacBasicField.Providers.displayField),
    id(StacBasicField.UUID.searchField, StacBasicField.UUID.displayField);

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

    @Override
    public String toString() {
        return searchField;
    }

}
