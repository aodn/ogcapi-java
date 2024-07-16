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
    dataset_provider(StacSummeries.DatasetProvider.searchField, StacSummeries.DatasetProvider.displayField, StacSummeries.DatasetProvider.sortField),
    dataset_group(StacSummeries.DatasetGroup.searchField, StacSummeries.DatasetGroup.displayField, StacSummeries.DatasetGroup.sortField),
    update_frequency(StacSummeries.UpdateFrequency.searchField, StacSummeries.UpdateFrequency.displayField, StacSummeries.UpdateFrequency.sortField),
    geometry(StacSummeries.Geometry.searchField, StacSummeries.Geometry.searchField, StacSummeries.Geometry.sortField),
    bbox(StacSummeries.Geometry.searchField, StacSummeries.Geometry.displayField, StacSummeries.Geometry.sortField),
    temporal(StacSummeries.Temporal.searchField, StacSummeries.Temporal.displayField, StacSummeries.Temporal.sortField),
    title(StacBasicField.Title.searchField, StacBasicField.Title.displayField, StacBasicField.Title.sortField),
    description(StacBasicField.Description.searchField, StacBasicField.Description.displayField, StacBasicField.Description.sortField),
    category(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField, StacBasicField.DiscoveryCategories.sortField),
    providers(StacBasicField.Providers.searchField, StacBasicField.Providers.displayField, StacBasicField.Providers.sortField),
    discovery_categories(StacBasicField.DiscoveryCategories.searchField, StacBasicField.DiscoveryCategories.displayField, StacBasicField.DiscoveryCategories.sortField),
    id(StacBasicField.UUID.searchField, StacBasicField.UUID.displayField, StacBasicField.UUID.sortField),
    links(StacBasicField.Links.searchField, StacBasicField.Links.displayField, StacBasicField.Links.sortField),
    status(StacSummeries.Status.searchField, StacSummeries.Status.displayField, StacSummeries.Status.sortField),
    score(CQLElasticSetting.score.getSetting(), CQLElasticSetting.score.getSetting(), CQLElasticSetting.score.getSetting()),
    ;

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
