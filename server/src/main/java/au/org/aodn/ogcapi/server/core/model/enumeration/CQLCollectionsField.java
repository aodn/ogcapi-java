package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 *
 * searchField is the field that you do the search
 * displayField is the field that you want to return from the elastic search result
 *
 */
public enum CQLCollectionsField {
    dataset_provider(StacSummeries.DatasetProvider.searchField, StacSummeries.DatasetProvider.displayField),
    dataset_group(StacSummeries.DatasetGroup.searchField, StacSummeries.DatasetGroup.displayField),
    update_frequency(StacSummeries.UpdateFrequency.searchField, StacSummeries.UpdateFrequency.displayField),
    geometry(StacSummeries.Geometry.searchField, StacSummeries.Geometry.searchField),
    bbox(StacSummeries.Geometry.searchField, StacSummeries.Geometry.displayField),
    temporal(StacSummeries.Temporal.searchField, StacSummeries.Temporal.displayField),
    title(StacBasicField.Title.searchField, StacBasicField.Title.displayField),
    description(StacBasicField.Description.searchField, StacBasicField.Description.displayField),
    category(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField),
    providers(StacBasicField.Providers.searchField, StacBasicField.Providers.displayField),
    discovery_categories(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField),
    id(StacBasicField.UUID.searchField, StacBasicField.UUID.displayField),
    links(StacBasicField.Links.searchField, StacBasicField.Links.displayField),
    status(StacSummeries.Status.searchField, StacSummeries.Status.displayField),
    ;

    private final String searchField;
    private final String displayField;

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
    /**
     * Given param, find any of those is not a valid CQLCollectionsField
     * @param args
     * @return
     */
    public static List<String> findInvalidEnum(List<String> args) {
        return args.stream()
                .filter(str -> {
                    try {
                        CQLCollectionsField.valueOf(str);
                        return false;
                    }
                    catch (IllegalArgumentException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }
}
