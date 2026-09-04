package au.org.aodn.ogcapi.server.core.model.enumeration;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.TopLeftBottomRightGeoBounds;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Field name for cloud optimized data index
 */
public enum CQLFeatureFields implements CQLFieldsInterface {

    id(
            StacBasicField.UUID.searchField,
            StacBasicField.UUID.displayField,
            null,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.UUID.sortField).order(order))
    ),
    collection(
            StacBasicField.Collection.searchField,
            StacBasicField.Collection.displayField,
            null,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.Collection.sortField).order(order))
    ),
    temporal(
            "properties.time",
            "properties.time",
            null,
            null,
            null
    ),
    count(
            "properties.count",
            "properties.count",
            null,
            null,
            null
    ),
    geometry(
            "geometry",
            "geometry",
            null,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field("geometry.geometry.coordinates").order(order))
    ),
    lat(
            "properties.lat",
            "properties.lat",
            null,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field("properties.lat").order(order))
    ),
    lng(
            "properties.lng",
            "properties.lng",
            null,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field("properties.lng").order(order))
    );

    // Field that use to do sort, elastic search treat FieldData (searchField) differently, a searchField is not
    // efficient for sorting.
    public final String searchField;    // Field in STAC object

    @Getter
    private final List<String> displayField;

    // null value indicate it cannot be sort by that field, elastic schema change need to add keyword field in order to
    // do search
    @Getter
    private final Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder;

    // We provided a default match query but there are cases where it isn't enough and need more complex
    // match, one example is multiple field. Move this logic out of the parser make it easier to read
    @Getter
    private final Function<String, Query> overridePropertyEqualsToQuery;

    // We provided a default match query but there are cases where it isn't enough and need more complex
    // match, one example is multiple field. Move this logic out of the parser make it easier to read
    @Getter
    private final Function<List<String>, Query> overridePropertyInQuery;

    CQLFeatureFields(String fields,
                     String displayField,
                     Function<String, Query> overridePropertyEqualsToQuery,
                     Function<List<String>, Query> overridePropertyInQuery,
                     Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {

        this(fields, List.of(displayField), overridePropertyEqualsToQuery, overridePropertyInQuery, sortBuilder);
    }

    CQLFeatureFields(String fields,
                     List<String> displayField,
                     Function<String, Query> overridePropertyEqualsToQuery,
                     Function<List<String>, Query> overridePropertyInQuery,
                     Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {

        this.searchField = fields;
        this.displayField = displayField;
        this.overridePropertyEqualsToQuery = overridePropertyEqualsToQuery;
        this.overridePropertyInQuery = overridePropertyInQuery;
        this.sortBuilder = sortBuilder;
    }

    @Override
    public Query getPropertyEqualToQuery(String literal) {
        return null;
    }

    @Override
    public Query getIntersectsQuery(String literal) {
        return null;
    }

    @Override
    public Query getIsNullQuery() {
        return null;
    }

    @Override
    public Query getLikeQuery(String literal) {
        return null;
    }

    @Override
    public Query getPropertyGreaterThanOrEqualsToQuery(String literal) {
        return null;
    }

    @Override
    public Query getBoundingBoxQuery(TopLeftBottomRightGeoBounds tlbr) {
        return null;
    }

    @Override
    public Query getPropertyInQuery(List<String> literals) { return null; }

    /**
     * Given param, find any of those is not a valid CQLCollectionsField
     * @param args -
     * @return Invalid enum
     */
    public static List<String> findInvalidEnum(List<String> args) {
        return args.stream()
                .filter(str -> {
                    try {
                        CQLFeatureFields.valueOf(str);
                        return false;
                    }
                    catch (IllegalArgumentException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }
}
