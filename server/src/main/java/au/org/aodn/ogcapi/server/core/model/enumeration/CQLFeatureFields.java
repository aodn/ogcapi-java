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
            "id",
            "id",
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field("id.keyword").order(order))
    ),
    collection(
            "collection",
            "collection",
            null,
            null
    ),
    temporal(
            "properties.time",
            "properties.time",
            null,
            null
    ),
    count(
            "properties.count",
            "properties.count",
            null,
            null
    ),
    geometry(
            "geometry",
            "geometry",
            null,
            null
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


    CQLFeatureFields(String fields,
                     String displayField,
                     Function<String, Query> overridePropertyEqualsToQuery,
                     Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {

        this(fields, List.of(displayField), overridePropertyEqualsToQuery, sortBuilder);
    }

    CQLFeatureFields(String fields,
                     List<String> displayField,
                     Function<String, Query> overridePropertyEqualsToQuery,
                     Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {

        this.searchField = fields;
        this.displayField = displayField;
        this.overridePropertyEqualsToQuery = overridePropertyEqualsToQuery;
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
