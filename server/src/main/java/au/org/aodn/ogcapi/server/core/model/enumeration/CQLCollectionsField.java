package au.org.aodn.ogcapi.server.core.model.enumeration;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.util.ObjectBuilder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 * searchField is the field that you do the search
 * displayField is the field that you want to return from the elastic search result
 *
 */
@Getter
public enum CQLCollectionsField {
    dataset_provider(
            StacSummeries.DatasetProvider.searchField,
            StacSummeries.DatasetProvider.displayField,
            null
    ),
    dataset_group(
            StacSummeries.DatasetGroup.searchField,
            StacSummeries.DatasetGroup.displayField,
            null
    ),
    update_frequency(
            StacSummeries.UpdateFrequency.searchField,
            StacSummeries.UpdateFrequency.displayField,
            null
    ),
    geometry(
            StacSummeries.Geometry.searchField,
            StacSummeries.Geometry.searchField,
            null
    ),
    bbox(
            StacSummeries.Geometry.searchField,
            StacSummeries.Geometry.displayField,
            null
    ),
    temporal(
            StacSummeries.Temporal.searchField,
            StacSummeries.Temporal.displayField,
            null
    ),
    title(
            StacBasicField.Title.searchField,
            StacBasicField.Title.displayField,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.Title.sortField).order(order))
    ),
    description(
            StacBasicField.Description.searchField,
            StacBasicField.Description.displayField,
            null
    ),
    category(
            StacBasicField.DiscoveryCategories.searchField,
            StacBasicField.DiscoveryCategories.displayField,
            null
    ),
    providers(
            StacBasicField.Providers.searchField,
            StacBasicField.Providers.displayField,
            null
    ),
    discovery_categories(
            StacBasicField.DiscoveryCategories.searchField,
            StacBasicField.DiscoveryCategories.displayField,
            null
    ),
    id(
            StacBasicField.UUID.searchField,
            StacBasicField.UUID.displayField,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.UUID.sortField).order(order))
    ),
    links(
            StacBasicField.Links.searchField,
            StacBasicField.Links.displayField,
            null
    ),
    status(
            StacSummeries.Status.searchField,
            StacSummeries.Status.displayField,
            null
    ),
    score(
            CQLElasticSetting.score.getSetting(),
            CQLElasticSetting.score.getSetting(),
            (order) -> new SortOptions.Builder().field(f -> f.field(CQLElasticSetting.score.getSetting()).order(order))
    ),
    ;
    // null value indicate it cannot be sort by that field, elastic schema change need to add keyword field in order to
    // do search
    private final Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder;
    private final String searchField;
    private final String displayField;

    CQLCollectionsField(String field, String displayField, Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {
        this.searchField = field;
        this.sortBuilder = sortBuilder;
        this.displayField = displayField;
    }

    @Override
    public String toString() {
        return searchField;
    }
    /**
     * Given param, find any of those is not a valid CQLCollectionsField
     * @param args -
     * @return Invalid enum
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
