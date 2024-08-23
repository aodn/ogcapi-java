package au.org.aodn.ogcapi.server.core.model.enumeration;

import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.ObjectBuilder;
import lombok.Getter;

import java.io.StringReader;
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
public enum CQLFields implements CQLFieldsInterface {
    dataset_provider(
            StacSummeries.DatasetProvider.searchField,
            StacSummeries.DatasetProvider.displayField,
            null,
            null
    ),
    dataset_group(
            StacSummeries.DatasetGroup.searchField,
            StacSummeries.DatasetGroup.displayField,
            null,
            null
    ),
    update_frequency(
            StacSummeries.UpdateFrequency.searchField,
            StacSummeries.UpdateFrequency.displayField,
            null,
            null
    ),
    geometry(
            StacSummeries.Geometry.searchField,
            StacSummeries.Geometry.searchField,
            null,
            null
    ),
    bbox(
            StacSummeries.Geometry.searchField,
            StacSummeries.Geometry.displayField,
            null,
            null
    ),
    temporal(
            StacSummeries.Temporal.searchField,
            StacSummeries.Temporal.displayField,
            null,
            /* You need to test this in elastic console, basically if end is null aka not exist then set the value
             * to max, else convert the time to epochMilli secs and get the largest if multiple exist,
             * so it means, when null it is on going and always, then follow by some valid large end dates
             * desc order always make on going on top.
             * {
             * "_script": {
             *  "type": "number",
             *  "nested": {
             *      "path": "summaries.temporal"
             *   },
             *   "script": {
             *      "lang": "painless",
             *      "source": """
             *          if (doc['summaries.temporal.end'].size() == 0) {
             *              return Double.MAX_VALUE;
             *          }
                        else {
                            return doc['summaries.temporal.end'].stream()
                                .mapToLong(f -> f.toEpochMilli())
                                .max()
                                .getAsLong()
                        }
             *         """
             *       },
             *       "order": "desc"
             *     }
             *   }
             */
            (order) -> new SortOptions.Builder().script(s -> s
                    .type(ScriptSortType.Number)
                    .nested(NestedSortValue.of(p -> p.path(StacSummeries.Temporal.sortField)))
                    .script(script -> script.inline(line -> line
                            .lang("painless")
                            .source("if (doc['" + StacSummeries.TemporalEnd.searchField + "'].size() == 0) {" +
                                          "  return Long.MAX_VALUE; " +
                                    "     } " +
                                    "     else {" +
                                    "       return doc['" + StacSummeries.TemporalEnd.searchField + "'].stream()" +
                                    "           .mapToLong(f -> f.toEpochMilli())" +
                                    "           .max()" +
                                    "           .getAsLong()" +
                                    "     }"
                            ))
                    ).order(order)
            )
    ),
    title(
            StacBasicField.Title.searchField,
            StacBasicField.Title.displayField,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.Title.sortField).order(order))
    ),
    description(
            StacBasicField.Description.searchField,
            StacBasicField.Description.displayField,
            null,
            null
    ),
    parameter_vocab(
            StacBasicField.ParameterVocabs.searchField,
            StacBasicField.ParameterVocabs.displayField,
            null,
            null
    ),
    providers(
            StacBasicField.Providers.searchField,
            StacBasicField.Providers.displayField,
            null,
            null
    ),
    parameter_vocabs(
            StacBasicField.ParameterVocabs.searchField,
            StacBasicField.ParameterVocabs.displayField,
            null,
            null
    ),
    id(
            StacBasicField.UUID.searchField,
            StacBasicField.UUID.displayField,
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field(StacBasicField.UUID.sortField).order(order))
    ),
    links(
            StacBasicField.Links.searchField,
            StacBasicField.Links.displayField,
            null,
            null
    ),
    status(
            StacSummeries.Status.searchField,
            StacSummeries.Status.displayField,
            null,
            null
    ),
    score(
            CQLElasticSetting.score.getSetting(),
            CQLElasticSetting.score.getSetting(),
            null,
            (order) -> new SortOptions.Builder().field(f -> f.field(CQLElasticSetting.score.getSetting()).order(order))
    ),
    fuzzy_title(
            null,
            StacBasicField.Title.displayField,
            (literal) -> MatchQuery.of(m -> m
                    .fuzziness("AUTO")
                    .field(StacBasicField.Title.searchField)
                    // At least first 3 char need to match before fuzzy to improve speed
                    .prefixLength(3)
                    .query(literal))._toQuery(),
            null
    ),
    fuzzy_description(
            null,
            StacBasicField.Description.displayField,
            (literal) -> MatchQuery.of(m -> m
                    .fuzziness("AUTO")
                    .field(StacBasicField.Description.searchField)
                    // At least first 3 char need to match before fuzzy to improve speed
                    .prefixLength(3)
                    .query(literal))._toQuery(),
            null
    ),
    fuzzy_content(
            null,
            List.of(StacBasicField.Title.displayField, StacBasicField.Description.displayField),
            (literal) -> MultiMatchQuery.of(m -> m
                    .fuzziness("AUTO")
                    .fields(List.of(StacBasicField.Title.searchField, StacBasicField.Description.searchField))
                    // At least first 3 char need to match before fuzzy to improve speed
                    .prefixLength(3)
                    .query(literal))._toQuery(),
            null
    ),
    ;
    private final String searchField;

    // null value indicate it cannot be sort by that field, elastic schema change need to add keyword field in order to
    // do search
    @Getter
    private final Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder;

    // We provided a default match query but there are cases where it isn't enough and need more complex
    // match, one example is multiple field. Move this logic out of the parser make it easier to read
    @Getter
    private final Function<String, Query> overridePropertyEqualsToQuery;

    @Getter
    private final List<String> displayField;

    CQLFields(String fields,
              String displayField,
              Function<String, Query> overridePropertyEqualsToQuery,
              Function<SortOrder, ObjectBuilder<SortOptions>> sortBuilder) {

        this(fields, List.of(displayField), overridePropertyEqualsToQuery, sortBuilder);
    }

    CQLFields(String fields,
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
        if(getOverridePropertyEqualsToQuery() == null) {
            return MatchPhraseQuery.of(builder -> builder
                    .field(this.searchField)
                    .query(literal)
            )._toQuery();
        }
        else {
            return getOverridePropertyEqualsToQuery().apply(literal);
        }
    }

    @Override
    public Query getPropertyGreaterThanOrEqualsToQuery(String literal) {
        return  RangeQuery.of(builder -> builder
                .field(this.searchField)
                .gte(JsonData.of(literal))
        )._toQuery();
    }

    @Override
    public Query getIntersectsQuery(String literal) {
        return new GeoShapeQuery.Builder()
                .field(this.searchField)
                .shape(builder -> builder
                        .relation(GeoShapeRelation.Intersects)
                        .shape(JsonData.from(new StringReader(literal))))
                .build()
                ._toQuery();
    }

    @Override
    public Query getIsNullQuery() {
        Query fieldExist = ExistsQuery.of(f -> f
                        .field(this.searchField))._toQuery();

        return BoolQuery.of(b -> b
                .mustNot(fieldExist))._toQuery();
    }

    @Override
    public Query getLikeQuery(String literal) {
        return RegexpQuery.of(f -> f
                .field(this.searchField)
                .caseInsensitive(true)
                .flags("ALL")
                .value(literal))._toQuery();
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
                        CQLFields.valueOf(str);
                        return false;
                    }
                    catch (IllegalArgumentException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }
}
