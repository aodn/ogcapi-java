package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.CategorySuggestDTO;
import au.org.aodn.ogcapi.server.core.model.RecordSuggestDTO;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.CQLToElasticFilterFactory;
import au.org.aodn.ogcapi.server.core.parser.ElasticFilter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.search.*;
import co.elastic.clients.elasticsearch.core.search_mvt.GridType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElasticSearch implements Search {
    protected Logger logger = LoggerFactory.getLogger(ElasticSearch.class);

    @Value("${elasticsearch.index.name}")
    protected String indexName;

    protected ElasticsearchClient esClient;

    @Autowired
    protected ObjectMapper mapper;

    @Value("${elasticsearch.search_as_you_type.record_suggest.path}")
    protected String searchAsYouTypeFieldsPath;

    @Value("${elasticsearch.search_as_you_type.record_suggest.fields}")
    protected String[] searchAsYouTypeEnabledFields;


    /*
     * this secondLevelCategorySuggestFilters for accessing the search_as_you_type "label" field
     * of the second level categories (discovery_category index) will never be changed unless the schema is changed,
     * or the discovery_category index is no longer be used
     */
    protected Query secondLevelCategorySuggestFilters = Query.of(q -> q.bool(b -> b.filter(f -> f.nested(n -> n.path("broader")
            .query(qq -> qq.exists(e -> e.field("broader")))))));


    @Value("${elasticsearch.search_as_you_type.category_suggest.field}")
    protected String secondLevelCategorySuggestField;

    @Value("${elasticsearch.search_as_you_type.category_suggest.index_name}")
    protected String categorySuggestIndex;

    public ElasticSearch(ElasticsearchClient client) {
        this.esClient = client;
    }

    protected BoolQuery createBoolQueryForProperties(List<Query> must, List<Query> should, List<Query> filters) {
        BoolQuery.Builder builder = new BoolQuery.Builder();

        if(must != null && !must.isEmpty()) {
            builder.must(must);
        }
        else {
            /*
             Equals to "must": { "match_all": {} }, that is match any, without this empty bool will fail if filter exist
             */
            builder.must(QueryBuilders.matchAll().build()._toQuery());
        }

        if(should != null && !should.isEmpty()) {
            builder.minimumShouldMatch("1");
            builder.should(should);
        }

        if(filters != null && !filters.isEmpty()) {
            builder.filter(filters);
        }
        return builder.build();
    }

    protected SearchRequest buildSearchAsYouTypeRequest(List<String> destinationFields, String indexName, List<Query> searchAsYouTypeQueries, List<Query> filters) {
        return new SearchRequest.Builder()
                .index(indexName)
                .source(SourceConfig.of(sc -> sc.filter(f -> f.includes(destinationFields))))
                .query(b -> b.bool(createBoolQueryForProperties(searchAsYouTypeQueries, null, filters)))
                .build();
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

    protected List<Hit<RecordSuggestDTO>> getRecordSuggestions(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        // create query
        List<Query> recordSuggestFieldsQueries = new ArrayList<>();
        Stream.of(searchAsYouTypeEnabledFields).forEach(field -> {
            String suggestField = searchAsYouTypeFieldsPath + "." + field;
            recordSuggestFieldsQueries.add(this.generateSearchAsYouTypeQuery(input, suggestField));
        });
        Query searchAsYouTypeQuery = Query.of(q -> q.nested(n -> n
                .path(searchAsYouTypeFieldsPath)
                .query(bQ -> bQ.bool(b -> b.should(recordSuggestFieldsQueries)))
        ));

        /*
            this is where the discovery categories filter is applied
            use term query for exact match of the categories
            (e.g you don't want "something", "something special" and "something secret" be returned when searching for "something")
            see more: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-terms-query.html#query-dsl-terms-query
            this query uses AND operator for the categories (e.g "wave" AND "temperature")
        */
        List<Query> filters;
        if (cql != null) {
            CQLToElasticFilterFactory<CQLCollectionsField> factory = new CQLToElasticFilterFactory<>(coor, CQLCollectionsField.class);
            Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);
            if (filter instanceof ElasticFilter elasticFilter) {
                filters = List.of(elasticFilter.getQuery());
            } else {
                filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
            }
        } else {
            filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
        }

        // create request
        SearchRequest searchRequest = this.buildSearchAsYouTypeRequest(List.of("title"), indexName, List.of(searchAsYouTypeQuery), filters);

        // execute
        logger.info("getRecordSuggestions | Elastic search payload {}", searchRequest.toString());
        SearchResponse<RecordSuggestDTO> response = esClient.search(searchRequest, RecordSuggestDTO.class);
        logger.info("getRecordSuggestions | Elastic search response {}", response);

        // return
        return response.hits().hits();
    }

    protected List<Hit<CategorySuggestDTO>> getCategorySuggestions(String input) throws IOException {
        // create query
        Query secondLevelCategorySuggestQuery = this.generateSearchAsYouTypeQuery(input, secondLevelCategorySuggestField);

        // create request
        SearchRequest searchRequest = this.buildSearchAsYouTypeRequest(List.of("label"), categorySuggestIndex, List.of(secondLevelCategorySuggestQuery), List.of(secondLevelCategorySuggestFilters));

        // execute
        logger.info("getCategorySuggestions | Elastic search payload {}", searchRequest.toString());
        SearchResponse<CategorySuggestDTO> response = esClient.search(searchRequest, CategorySuggestDTO.class);
        logger.info("getCategorySuggestions | Elastic search response {}", response);

        // return
        return response.hits().hits();
    }

    public ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        // extract category suggestions
        Set<String> categorySuggestions = new HashSet<>();
        for (Hit<CategorySuggestDTO> item : this.getCategorySuggestions(input)) {
            if (item.source() != null) {
                categorySuggestions.add(item.source().getLabel());
            }
        }

        // extract title suggestions
        List<String> recordTitleSuggestions = new ArrayList<>();
        for (Hit<RecordSuggestDTO> item : this.getRecordSuggestions(input, cql, coor)) {
            if (item.source() != null) {
                recordTitleSuggestions.add(item.source().getTitle());
            }
        }

        Map<String, Object> allSuggestions = new HashMap<>();
        allSuggestions.put("category_suggestions", new ArrayList<>(categorySuggestions));

        Map<String, List<String>> recordSuggestions = new HashMap<>();
        recordSuggestions.put("titles", recordTitleSuggestions);

        allSuggestions.put("record_suggestions", recordSuggestions);

        return new ResponseEntity<>(allSuggestions, HttpStatus.OK);
    }

    protected List<StacCollectionModel> searchCollectionBy(List<Query> queries,
                                                           List<Query> should,
                                                           List<Query> filters,
                                                           List<String> properties,
                                                           Integer from,
                                                           Integer size) throws IOException {



        SearchRequest.Builder builder = new SearchRequest.Builder();

        builder.index(indexName)
                .size(size)         // Max hit to return
                .from(from)         // Skip how many record
                .query(q -> q.bool(createBoolQueryForProperties(queries, should, filters)))
                .size(2000)
                .sort(so -> so.score(v -> v.order(SortOrder.Desc)))
                .sort(so -> so
                    .field(FieldSort.of(f -> f
                        .field(StacSummeries.Score.searchField)
                        .order(SortOrder.Desc))
                    ));

        if(properties != null && !properties.isEmpty()) {
            // Convert the income field name to the real field name in STAC
            List<String> fs = properties
                    .stream()
                    .map(v -> CQLCollectionsField.valueOf(v).getDisplayField())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Set fetch to false so that it do not return the original document but just the field
            // we set
            builder.source(f -> f
                    .filter(sf -> sf
                            .includes(fs)
                    )
            );
        }
        else {
            builder.source(f -> f.fetch(true));
        }

        SearchRequest request = builder.build();
        logger.debug("Final elastic search payload {}", request.toString());

        try {
            SearchResponse<ObjectNode> response = esClient.search(request, ObjectNode.class);

            return response
                    .hits()
                    .hits()
                    .stream()
                    .map(this::formatResult)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        catch(ElasticsearchException ee) {
            logger.warn("Elastic exception on query, reason is {}", ee.error().rootCause());
            throw ee;
        }
    }

    protected StacCollectionModel formatResult(Hit<ObjectNode> nodes) {
        try {
            if(nodes.source() != null) {
                String json = nodes.source().toPrettyString();
                logger.debug("Serialize STAC json to StacCollectionModel {}", json);

                return mapper.readValue(json, StacCollectionModel.class);
            }
            else {
                logger.error("Failed to serialize text to StacCollectionModel");
                return null;
            }
        }
        catch (JsonProcessingException e) {
            logger.error("Exception failed to convert text to StacCollectionModel", e);
            return null;
        }
    }

    protected List<StacCollectionModel> searchCollectionsByIds(List<String> ids, Boolean isWithGeometry) throws IOException {

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

        return searchCollectionBy(queries, null, filters, null, null, null);
    }

    @Override
    public List<StacCollectionModel> searchCollectionWithGeometry(List<String> ids) throws Exception {
        return searchCollectionsByIds(ids, Boolean.TRUE);
    }

    @Override
    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        return searchCollectionsByIds(null, Boolean.TRUE);
    }

    @Override
    public List<StacCollectionModel> searchCollections(List<String> ids) throws Exception {
        return searchCollectionsByIds(ids, Boolean.FALSE);
    }

    @Override
    public List<StacCollectionModel> searchAllCollections() throws IOException {
        return searchCollectionsByIds(null, Boolean.FALSE);
    }

    @Override
    public List<StacCollectionModel> searchByParameters(List<String> keywords, String cql, CQLCrsType coor, List<String> properties) throws IOException, CQLException {

        if((keywords == null || keywords.isEmpty()) && cql == null) {
            return searchAllCollections();
        }
        else {

            List<Query> queries = null;
            if(keywords != null && !keywords.isEmpty()) {
                queries = new ArrayList<>();

                for (String t : keywords) {
                    Query q = MultiMatchQuery.of(m -> m
                            .fuzziness("AUTO")
                            //TODO: what keywords we want to search?
                            .fields(StacBasicField.Title.searchField, StacBasicField.Description.searchField)
                            .prefixLength(0)
                            .query(t))._toQuery();
                    queries.add(q);
                }
            }

            List<Query> filters = new ArrayList<>();
            if(cql != null) {
                CQLToElasticFilterFactory<CQLCollectionsField> factory = new CQLToElasticFilterFactory<>(coor, CQLCollectionsField.class);
                Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);
                if(filter instanceof ElasticFilter elasticFilter) {
                    filters = List.of(elasticFilter.getQuery());
                }
            }
            return searchCollectionBy(null, queries, filters,  properties, null, null);
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

        logger.debug("Final elastic search mvt payload {}", builder);

        return esClient.searchMvt(builder.build());
    }
}
