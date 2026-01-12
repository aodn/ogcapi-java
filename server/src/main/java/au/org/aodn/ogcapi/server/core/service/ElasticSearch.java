package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.server.core.model.EsFeatureCollectionModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.SearchSuggestionsModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.elastic.CQLToElasticFilterFactory;
import au.org.aodn.ogcapi.server.core.parser.elastic.QueryHandler;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search_mvt.GridType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.ELASTIC_SEARCH_UUID_ONLY;

@Slf4j
public class ElasticSearch extends ElasticSearchBase implements Search {

    protected Map<CQLElasticSetting, String> defaultElasticSetting;

    @Value("${elasticsearch.search_as_you_type.search_suggestions.path}")
    protected String searchAsYouTypeFieldsPath;

    @Value("${elasticsearch.search_as_you_type.search_suggestions.fields}")
    protected String[] searchAsYouTypeEnabledFields;

    @Value("${elasticsearch.cloud_optimized_index.name}")
    protected String dataIndexName;

    @Value("${elasticsearch.search_after.split_regex:\\|\\|}")
    protected String searchAfterSplitRegex;

    public ElasticSearch(ElasticsearchClient client,
                         CacheNoLandGeometry cacheNoLandGeometry,
                         ObjectMapper mapper,
                         String indexName,
                         Integer pageSize,
                         Integer searchAsYouTypeSize) {

        this.setEsClient(client);
        this.setMapper(mapper);
        this.setIndexName(indexName);
        this.setPageSize(pageSize);
        this.setSearchAsYouTypeSize(searchAsYouTypeSize);
        this.setCacheNoLandGeometry(cacheNoLandGeometry);
        this.defaultElasticSetting = CQLToElasticFilterFactory.getDefaultSetting();
    }

    protected Query generateSearchAsYouTypeQuery(String input, String suggestField) {
        return Query.of(q -> q.multiMatch(mm -> mm
            .query(input)
            .fuzziness("AUTO")
            /*
             * TODO: need to observe the behaviour of different types and pick the best one for our needs,
             * phrase_prefix type produces the most similar effect to the completion suggester but ElasticSearch says it is not the best choice:
             *   > To search for documents that strictly match the query terms in order, or to search using other properties of phrase queries, use a match_phrase_prefix query on the root field.
             *   > A match_phrase query can also be used if the last term should be matched exactly, and not as a prefix. Using phrase queries may be less efficient than using the match_bool_prefix query.
             * ElasticSearch recommends using bool_prefix type: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-as-you-type.html
             *   > The most efficient way of querying to serve a search-as-you-type use case is usually a multi_match query of type bool_prefix that targets the root search_as_you_type field and its shingle subfields.
             *   > This can match the query terms in any order, but will score documents higher if they contain the terms in order in a shingle subfield.
             * Also, if using phrase_prefix, it is not allowed to use fuzziness parameter:
             *   > Fuzziness not allowed for type [phrase_prefix]
             */
            .type(TextQueryType.BoolPrefix)
            // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-as-you-type.html#specific-params
            .fields(Arrays.asList(suggestField, suggestField+"._2gram", suggestField+"._3gram"))
        ));
    }

    protected List<Hit<SearchSuggestionsModel>> getSuggestionsByField(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        // create query
        List<Query> suggestFieldsQueries = new ArrayList<>();
        Stream.of(searchAsYouTypeEnabledFields).forEach(field -> {
            String suggestField = searchAsYouTypeFieldsPath + "." + field;
            suggestFieldsQueries.add(this.generateSearchAsYouTypeQuery(input, suggestField));
        });
        Query searchAsYouTypeQuery = Query.of(q -> q.nested(n -> n
                .path(searchAsYouTypeFieldsPath)
                .query(bQ -> bQ.bool(b -> b.should(suggestFieldsQueries)))
        ));

        /*
            this is where the discovery parameter vocabs filter is applied
            use term query for exact match of the parameter vocabs
            (e.g you don't want "something", "something special" and "something secret" be returned when searching for "something")
            see more: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-terms-query.html#query-dsl-terms-query
            this query uses AND operator for the parameter vocabs (e.g "wave" AND "temperature")
        */
        List<Query> filters;
        if (cql != null) {
            CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(coor, CQLFields.class);
            Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);
            if (filter instanceof QueryHandler elasticFilter) {
                filters = List.of(elasticFilter.getQuery());
            } else {
                // If no filter, then use the match_all{} to get all record
                filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
            }
        } else {
            // If no filter, then use the match_all{} to get all record
            filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
        }

        // create request
        SearchRequest searchRequest = this.buildSearchAsYouTypeRequest(
                Stream.of(searchAsYouTypeEnabledFields).map(destination -> searchAsYouTypeFieldsPath + "." + destination).toList(),
                indexName,
                List.of(searchAsYouTypeQuery),
                filters);

        // execute
        log.info("getRecordSuggestions | Elastic search payload {}", searchRequest.toString());
        SearchResponse<SearchSuggestionsModel> response = esClient.search(searchRequest, SearchSuggestionsModel.class);
        log.info("getRecordSuggestions | Elastic search response {}", response);

        // return
        return response.hits().hits();
    }

    public ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        Map<String, Set<String>> searchSuggestions = new HashMap<>();
        List<Hit<SearchSuggestionsModel>> suggestion = this.getSuggestionsByField(input, cql, coor);
        // extract parameter vocab suggestions
        Set<String> parameterVocabSuggestions = suggestion
                .stream()
                .filter(item -> item.source() != null && item.source().getParameterVocabs() != null && !item.source().getParameterVocabs().isEmpty())
                .flatMap(item -> item.source().getParameterVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_parameter_vocabs", parameterVocabSuggestions);

        Set<String> platformVocabSuggestions = suggestion
                .stream()
                .filter(item -> item.source() != null && item.source().getPlatformVocabs() != null && !item.source().getPlatformVocabs().isEmpty())
                .flatMap(item -> item.source().getPlatformVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_platform_vocabs", platformVocabSuggestions);

        Set<String> organisationVocabSuggestions = suggestion
                .stream()
                .filter(item -> item.source() != null && item.source().getOrganisationVocabs() != null && !item.source().getOrganisationVocabs().isEmpty())
                .flatMap(item -> item.source().getOrganisationVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_organisation_vocabs", organisationVocabSuggestions);

        // extract abstract phrases suggestions
        Set<String> abstractPhrases = suggestion
                .stream()
                .filter(item -> item.source() != null && item.source().getAbstractPhrases() != null && !item.source().getAbstractPhrases().isEmpty())
                .flatMap(item -> item.source().getAbstractPhrases().stream())
                .filter(phrase -> phrase.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_phrases", abstractPhrases);

        return new ResponseEntity<>(searchSuggestions, HttpStatus.OK);
    }

    protected ElasticSearchBase.SearchResult<StacCollectionModel> searchCollectionsByIds(List<String> ids, Boolean isWithGeometry, String sortBy) {

        List<Query> queries = new ArrayList<>();
        queries.add(MatchQuery.of(m -> m
                            .field(StacType.searchField)
                            .query(StacType.Collection.value))._toQuery());

        if(isWithGeometry) {
            queries.add(ExistsQuery.of(m -> m
                    .field(StacSummeries.Geometry.searchField))._toQuery());
        }

        List<Query> filters = null;
        if(ids != null && !ids.isEmpty()) {
            List<FieldValue> values = ids.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList());

            filters = List.of(
                    TermsQuery.of(t -> t
                            .field(StacBasicField.UUID.searchField)
                            .terms(s -> s.value(values)))._toQuery()
            );
        }

        return searchCollectionBy(
                queries,
                null,
                filters,
                null,
                null,
                createSortOptions(sortBy, CQLFields.class),
                null,
                null);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchCollectionWithGeometry(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.TRUE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchAllCollectionsWithGeometry(String sortBy) {
        return searchCollectionsByIds(null, Boolean.TRUE, sortBy);
    }

    @Cacheable(value=ELASTIC_SEARCH_UUID_ONLY, key="#id")
    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchCollections(String id) {
        return searchCollections(List.of(id), null);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchCollections(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.FALSE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchAllCollections(String sortBy) {
        return searchCollectionsByIds(null, Boolean.FALSE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchByParameters(List<String> keywords, String cql, List<String> properties, String sortBy, CQLCrsType coor) throws CQLException {

        if((keywords == null || keywords.isEmpty()) && cql == null) {
            return searchAllCollections(sortBy);
        }
        else {

            List<Query> should = null;
            if(keywords != null && !keywords.isEmpty()) {
                should = new ArrayList<>();

                for (String t : keywords) {
                    should.add(CQLFields.fuzzy_title.getPropertyEqualToQuery(t));
                    should.add(CQLFields.fuzzy_desc.getPropertyEqualToQuery(t));
                    should.add(CQLFields.parameter_vocabs.getPropertyEqualToQuery(t));
                    should.add(CQLFields.organisation_vocabs.getPropertyEqualToQuery(t));
                    should.add(CQLFields.platform_vocabs.getPropertyEqualToQuery(t));
                    should.add(CQLFields.id.getPropertyEqualToQuery(t));
                    // A request to not using acronym in title and description in metadata, hence these
                    // acronym moved to links, for example NRMN record is mentioned in the link title.
                    // This is a work-around to the requirement but still allow use of NRMN
                    should.add(CQLFields.links_title_contains.getPropertyEqualToQuery(t));
                    should.add(CQLFields.credit_contains.getPropertyEqualToQuery(t));
                }
            }

            List<Query> filters = new ArrayList<>();

            CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(coor, CQLFields.class);
            if(cql != null) {
                Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);

                if(filter instanceof QueryHandler handler) {
                    if(handler.getErrors() == null || handler.getErrors().isEmpty()) {
                        if(handler.getQuery() != null) {
                            // There is no error during parsing
                            filters = List.of(handler.getQuery());
                        }
                    }
                    else {
                        throw new IllegalArgumentException(
                                "CQL Parse Error",
                                handler.getErrors()
                                        .stream()
                                        .reduce(null, (e1, e2) -> {
                                            if (e1 == null) return e2;
                                            e1.addSuppressed(e2);
                                            return e1;
                                        }));
                    }
                }
            }
            // Get the page size after parsing
            Map<CQLElasticSetting, String> setting = factory.getQuerySetting();
            Long maxSize = null;
            try {
                if(setting.get(CQLElasticSetting.page_size) != null &&
                    !setting.get(CQLElasticSetting.page_size).isBlank()) {
                    maxSize = Long.parseLong(setting.get(CQLElasticSetting.page_size));
                }
            }
            catch(NumberFormatException pe) {
                // Nothing to do as except null as default
            }
            // Get the score after parsing
            // TODO: !! It is not good to set score due to fact that the text search include match on filter
            // in case of text where filter is the only match, the score will become null (only fuzzy match have score)
            // then if you set a score, you have nothing match. In the future, this score should be removed if we
            // do not encounter a good use case. !!
            Double score = null;
            try {
                if (setting.get(CQLElasticSetting.score) != null &&
                        !setting.get(CQLElasticSetting.score).isBlank()) {
                    score = Double.parseDouble(setting.get(CQLElasticSetting.score));
                }
            }
            catch(Exception e) {
                log.warn("Error parsing score assume null", e);
                // OK to ignore as accept null as the value
            }
            // Get the search after
            List<FieldValue> searchAfter = null;
            if (setting.get(CQLElasticSetting.search_after) != null &&
                    !setting.get(CQLElasticSetting.search_after).isBlank()) {
                // Convert the regex separate string to List<FieldValue>
                searchAfter = Arrays.stream(setting.get(CQLElasticSetting.search_after)
                        .split(searchAfterSplitRegex))
                        .filter(v -> !v.isBlank())
                        .map(String::trim)
                        .map(ElasticSearch::toFieldValue)
                        .toList();
            }

            return searchCollectionBy(
                    null,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    createSortOptions(sortBy, CQLFields.class),
                    score,
                    maxSize
            );
        }
    }

    @Override
    public BinaryResponse searchCollectionVectorTile(List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol) throws IOException {

        SearchMvtRequest.Builder builder = new SearchMvtRequest.Builder();
        builder.index(indexName)
                .field(StacSummeries.Geometry.searchField)
                .zoom(tileMatrix)
                .x(tileRow)
                .y(tileCol)
                // If true, the meta layer’s feature is a bounding box resulting from a geo_bounds aggregation.
                // The aggregation runs on <field> values that intersect the <zoom>/<x>/<y> tile with wrap_longitude
                // set to false. The resulting bounding box may be larger than the vector tile.
                .exactBounds(Boolean.FALSE)
                .gridType(GridType.Grid);

        if(ids != null && !ids.isEmpty()) {
            List<FieldValue> values = ids.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList());

            List<Query> filters = List.of(
                    TermsQuery.of(t -> t
                            .field(StacBasicField.UUID.searchField)
                            .terms(s -> s.value(values)))._toQuery());

            builder.query(q -> q.bool(b -> b.filter(filters)));
        }

        log.debug("Final elastic search mvt payload {}", builder);

        return esClient.searchMvt(builder.build());
    }

    protected static FieldValue toFieldValue(String s) {
        try {
            Double v = Double.parseDouble(s.trim());
            return FieldValue.of(v);
        }
        catch(NumberFormatException e) {
            // Ok to ignore it as we will try other paring
        }

        try {
            Long v = Long.parseLong(s.trim());
            return FieldValue.of(v);
        }
        catch(NumberFormatException e) {
            // Ok to ignore it as we will try other paring
        }

        if(s.trim().equalsIgnoreCase("true") || s.trim().equalsIgnoreCase("false")) {
            Boolean v = Boolean.parseBoolean(s.trim());
            return FieldValue.of(v);
        }

        if(s.trim().startsWith(STR_INDICATOR)) {
            // UUID is part of the sort order, sometimes it will be an ID which is a
            // number and can be parsed directly by code above, so to avoid incorrect parsing
            // we will prefix it with STR_INDICATOR
            return FieldValue.of(s.replaceFirst(STR_INDICATOR, "").trim());
        }
        // Assume it is string
        return FieldValue.of(s.trim());
    }
    /**
     * We will need to create a aggregation for each of the feature query, this one target the summary feature
     * which create a summary of the indexed count group by geometry and date range for the cloud optimized data.
     * Below code equals this:
     * {
     *   "aggregations": {
     *     "coordinates": {
     *       "aggregations": {
     *         "total_count": {
     *           "sum": {
     *             "field": "properties.count"
     *           }
     *         },
     *         "max_time": {
     *           "max": {
     *             "field": "properties.time"
     *           }
     *         },
     *         "min_time": {
     *           "min": {
     *             "field": "properties.time"
     *           }
     *         },
     *         "coordinates": {
     *           "top_hits": {
     *             "size": 1,
     *             "sort": [
     *               {
     *                 "collection.keyword": {
     *                   "order": "asc"
     *                 }
     *               },
     *               {
     *                 "geometry.geometry.coordinates": {
     *                   "order": "asc"
     *                 }
     *               },
     *             ]
     *           }
     *         }
     *       },
     *       "composite": {
     *         "size": 2200,
     *         "sources": [
     *           {
     *             "collection": {
     *               "terms": {
     *                 "field": "collection.keyword"
     *               }
     *             }
     *           },
     *           {
     *             "coordinates": {
     *               "terms": {
     *                 "script": {
     *                      "source": "doc['geometry.geometry.coordinates'].value.toString()",
     *                      "lang": "painless"
     *                 }
     *               }
     *             }
     *           }
     *         ]
     *       }
     *     }
     *   },
     *   "size": 0
     * }
     *
     * @param collectionId - The metadata set id
     * @param properties - The field you want to return
     * @param filter - Any filter applied to the summary operation
     * @return - Result
     */
//    @Override
//    public ElasticSearchBase.SearchResult<StacItemModel> searchFeatureSummary(String collectionId, List<String> properties, String filter) {
//
//        final String COORDINATES = "coordinates";
//        final String TOTAL_COUNT = "total_count";
//        final String MIN_TIME = "min_time";
//        final String MAX_TIME = "max_time";
//
//        BiFunction<Map<String, FieldValue>, Map<String, FieldValue>, SearchRequest.Builder> builderSupplier = (
//                arguments, afterKey) -> {
//
//            SearchRequest.Builder builder = new SearchRequest.Builder();
//
//            builder.query(q -> q
//                            .term(t -> t
//                                    .field(CQLFeatureFields.collection.searchField)
//                                    .value(arguments.get("collectionId"))
//                            )
//                    );
//
//            // Group by lng
//            CompositeAggregationSource lng = CompositeAggregationSource.of(c -> c.terms(t -> t
//                    .field(CQLFeatureFields.lng.searchField)));
//
//            // Group by lat
//            CompositeAggregationSource lat = CompositeAggregationSource.of(c -> c.terms(t -> t
//                    .field(CQLFeatureFields.lat.searchField)));
//
//            // Use afterKey to page to another batch of records if exist
//            Aggregation compose = afterKey == null ?
//                    new Aggregation.Builder().composite(c -> c
//                                .sources(List.of(
//                                        Map.of(CQLFeatureFields.lng.name(), lng),
//                                        Map.of(CQLFeatureFields.lat.name(), lat))
//                                )
//                                .size(pageSize)
//                            ).build()
//                    :
//                    new Aggregation.Builder().composite(c -> c
//                                .sources(List.of(
//                                        Map.of(CQLFeatureFields.lng.name(), lng),
//                                        Map.of(CQLFeatureFields.lat.name(), lat))
//                                )
//                                .size(pageSize)
//                                .after(afterKey)
//                            ).build();
//
//
//            // Sum of count
//            Aggregation sum = SumAggregation.of(s -> s.field(CQLFeatureFields.count.searchField))._toAggregation();
//
//            // Min value of field
//            Aggregation min = MinAggregation.of(s -> s.field(CQLFeatureFields.temporal.searchField))._toAggregation();
//
//            // Max value of field
//            Aggregation max = MaxAggregation.of(s -> s.field(CQLFeatureFields.temporal.searchField))._toAggregation();
//
//            // Field value to return, think of it as select part of SQL
//            Aggregation field = new Aggregation.Builder().topHits(th -> th.size(1)
//                            .sort(createSortOptions(
//                                    String.format("%s,%s", CQLFeatureFields.lng.name(), CQLFeatureFields.lat.name()),
//                                    CQLFeatureFields.class)))
//                    .build();
//
//            Aggregation aggregation = new Aggregation.Builder()
//                    .composite(compose.composite())
//                    .aggregations(Map.of(
//                            TOTAL_COUNT, sum,
//                            MIN_TIME, min,
//                            MAX_TIME, max,
//                            COORDINATES, field
//                    ))
//                    .build();
//
//            // There is a limitation that all sort field, assume to be inside the properties
//            Aggregation nested = new Aggregation.Builder().nested(n -> n
//                            .path("properties")
//                    )
//                    .aggregations(COORDINATES, aggregation)
//                    .build();
//
//
//            builder.index(dataIndexName)
//                    .size(0)    // Do not return hits, only aggregations, that is the hits().hit() section will be empty
//                    .aggregations(COORDINATES, nested);
//
//            return builder;
//        };
//
//        try {
//            var queryTimer = new StopWatch();
//            queryTimer.start("query timer");
//            ElasticSearchBase.SearchResult<StacItemModel> result = new ElasticSearchBase.SearchResult<>();
//            result.setCollections(new ArrayList<>());
//
//            Map<String, FieldValue> arguments = Map.of(
//                    "collectionId", FieldValue.of(collectionId),
//                    "aggKey", FieldValue.of(COORDINATES)
//            );
//            Iterable<CompositeBucket> response = pageableAggregation(builderSupplier, CompositeBucket.class, arguments, null);
//
//            queryTimer.stop();
//            log.info(queryTimer.prettyPrint());
//            var analyzingTimer = new StopWatch();
//            analyzingTimer.start("analyzing timer");
//            for (CompositeBucket node : response) {
//                if (node != null) {
//                    StacItemModel. StacItemModelBuilder model = StacItemModel.builder();
//
//                    result.setTotal(result.getTotal() + node.docCount());
//
//                    TopHitsAggregate th = node.aggregations().get(COORDINATES).topHits();
//                    model.uuid(th.hits().hits().get(0).id());
//
//                    JsonData jd = th.hits().hits().get(0).source();
//                    if(jd != null) {
//                        Map<?, ?> map = jd.to(Map.class);
//                        BigDecimal lng = BigDecimal.valueOf((double)map.get("lng"));
//                        BigDecimal lat = BigDecimal.valueOf((double)map.get("lat"));
//                        model.geometry(Map.of("geometry", Map.of(
//                                "coordinates", List.of(lng, lat)
//                        )));
//                    }
//
//                    SumAggregate sa = node.aggregations().get(TOTAL_COUNT).sum();
//                    MinAggregate min = node.aggregations().get(MIN_TIME).min();
//                    MaxAggregate max = node.aggregations().get(MAX_TIME).max();
//
//                    model.properties(Map.of(
//                            FeatureProperty.COUNT.getValue(), sa.value(),
//                            FeatureProperty.START_TIME.getValue(), min.valueAsString() == null ? "" : min.valueAsString(),
//                            FeatureProperty.END_TIME.getValue(), max.valueAsString() == null ? "" : max.valueAsString()
//                    ));
//
//                    result.getCollections().add(model.build());
//                }
//            }
//            analyzingTimer.stop();
//            log.info(analyzingTimer.prettyPrint());
//            return result;
//        }
//        catch (Exception e) {
//            log.error("Error while searching dataset.", e);
//        }
//        return null;
//    }

    @Override
    public SearchResult<FeatureGeoJSON> searchFeatureSummary(String collectionId, List<String> properties, String filter) {
        try {
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(dataIndexName)
                    .query(q -> q.term(t -> t
                            .field("properties.collection.keyword")
                            .value(collectionId)
                    ))
                    .size(1000)
                    .build();

            var response = esClient.search(searchRequest, EsFeatureCollectionModel.class);

            SearchResult<FeatureGeoJSON> result = new SearchResult<>();
            List<FeatureGeoJSON> features = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                EsFeatureCollectionModel hitFeatureCollection = hit.source();
                if (hitFeatureCollection != null && hitFeatureCollection.getFeatures() != null) {
                    // A collectionID may map to several dataset key. So we need to group features by dataset keys. TO get a dataset key which sits in hit.properties.key. For example:
                    // "properties": {
                    //            "date": "2011-04",
                    //            "collection": "4d3d4aca-472e-4616-88a5-df0f5ab401ba",
                    //            "key": "mooring_acidification_realtime_qc.parquet"
                    //          }
                    String datasetKey = null;
                    if (hitFeatureCollection.getProperties() != null) {
                        Object keyObj = hitFeatureCollection.getProperties().get("key");
                        if (keyObj != null) {
                            datasetKey = keyObj.toString();
                        }
                    }

                    List<FeatureGeoJSON> documentFeatures =
                            hitFeatureCollection.toFeatureCollectionGeoJSON().getFeatures();

                    for (FeatureGeoJSON feature : documentFeatures) {
                        // add key in property field for each feature
                        if (datasetKey != null) {
                            Object featurePropsObj = feature.getProperties();

                            if (featurePropsObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> featurePropsMap = (Map<String, Object>) featurePropsObj;
                                featurePropsMap.put("key", datasetKey);
                            } else {
                                Map<String, Object> newPropsMap = new HashMap<>();
                                newPropsMap.put("key", datasetKey);
                                feature.setProperties(newPropsMap);
                            }
                        }
                        features.add(feature);
                    }
                }
            }

            log.info("feature size: {}", features.size());

            result.setCollections(features);
            if (response.hits().total() != null) {
                result.setTotal(response.hits().total().value());
            }

            return result;

        } catch (IOException e) {
            log.error("Error while searching dataset.", e);
        }
        return null;
    }
}
