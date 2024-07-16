package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

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
    dataset_provider(StacSummeries.DatasetProvider.searchField, StacSummeries.DatasetProvider.displayField, null),
    dataset_group(StacSummeries.DatasetGroup.searchField, StacSummeries.DatasetGroup.displayField, null),
    update_frequency(StacSummeries.UpdateFrequency.searchField, StacSummeries.UpdateFrequency.displayField, null),
    geometry(StacSummeries.Geometry.searchField, StacSummeries.Geometry.searchField, null),
    bbox(StacSummeries.Geometry.searchField, StacSummeries.Geometry.displayField, null),
    temporal(StacSummeries.Temporal.searchField, StacSummeries.Temporal.displayField, null),
    title(StacBasicField.Title.searchField, StacBasicField.Title.displayField, StacBasicField.Title.sortField),
    description(StacBasicField.Description.searchField, StacBasicField.Description.displayField, null),
    category(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField, null),
    providers(StacBasicField.Providers.searchField, StacBasicField.Providers.displayField, null),
    discovery_categories(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField, null),
    id(StacBasicField.UUID.searchField, StacBasicField.UUID.displayField, StacBasicField.UUID.sortField),
    links(StacBasicField.Links.searchField, StacBasicField.Links.displayField, null),
    status(StacSummeries.Status.searchField, StacSummeries.Status.displayField, null),
    score(CQLElasticSetting.score.getSetting(), CQLElasticSetting.score.getSetting(), CQLElasticSetting.score.getSetting()),
    ;
    // null value indicate it cannot be sort by that field, elastic schema change need to add keyword field in order to
    // do search
    @Getter
    private final String sortField;

    @Getter
    private final String searchField;

    @Getter
    private final String displayField;

    CQLCollectionsField(String field, String displayField, String order) {
        this.searchField = field;
        this.sortField = order;
        this.displayField = displayField;
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
