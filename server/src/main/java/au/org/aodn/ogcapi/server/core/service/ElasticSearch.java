package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.CategorySuggestDTO;
import au.org.aodn.ogcapi.server.core.model.RecordSuggestDTO;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.CQLToElasticFilterFactory;
import au.org.aodn.ogcapi.server.core.parser.QueryHandler;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.search_mvt.GridType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ElasticSearch extends ElasticSearchBase implements Search {

    protected Map<CQLElasticSetting, String> defaultElasticSetting;

    @Value("${elasticsearch.index.minScore:7}")
    protected Integer minScore;

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

    public ElasticSearch(ElasticsearchClient client,
                         ObjectMapper mapper,
                         String indexName,
                         Integer pageSize,
                         Integer searchAsYouTypeSize) {

        this.setEsClient(client);
        this.setMapper(mapper);
        this.setIndexName(indexName);
        this.setPageSize(pageSize);
        this.setSearchAsYouTypeSize(searchAsYouTypeSize);
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
            if (filter instanceof QueryHandler elasticFilter) {
                filters = List.of(elasticFilter.getQuery());
            } else {
                filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
            }
        } else {
            filters = List.of(MatchAllQuery.of(q -> q)._toQuery());
        }

        // create request
        SearchRequest searchRequest = this.buildSearchAsYouTypeRequest(
                List.of("record_suggest.abstract_phrases"),
                indexName,
                List.of(searchAsYouTypeQuery),
                filters);

        // execute
        log.info("getRecordSuggestions | Elastic search payload {}", searchRequest.toString());
        SearchResponse<RecordSuggestDTO> response = esClient.search(searchRequest, RecordSuggestDTO.class);
        log.info("getRecordSuggestions | Elastic search response {}", response);

        // return
        return response.hits().hits();
    }

    protected List<Hit<CategorySuggestDTO>> getCategorySuggestions(String input) throws IOException {
        // create query
        Query secondLevelCategorySuggestQuery = this.generateSearchAsYouTypeQuery(input, secondLevelCategorySuggestField);

        // create request
        SearchRequest searchRequest = this.buildSearchAsYouTypeRequest(
                List.of("label"),
                categorySuggestIndex,
                List.of(secondLevelCategorySuggestQuery),
                List.of(secondLevelCategorySuggestFilters));

        // execute
        log.info("getCategorySuggestions | Elastic search payload {}", searchRequest.toString());
        SearchResponse<CategorySuggestDTO> response = esClient.search(searchRequest, CategorySuggestDTO.class);
        log.info("getCategorySuggestions | Elastic search response {}", response);

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

        // extract abstract phrases suggestions
        List<String> abstractPhrases = this.getRecordSuggestions(input, cql, coor)
                .stream()
                .filter(item -> item.source() != null)
                .flatMap(item -> item.source().getAbstractPhrases().stream())
                .filter(phrase -> phrase.contains(input))
                .collect(Collectors.toList());

        Map<String, Object> allSuggestions = new HashMap<>();
        allSuggestions.put("category_suggestions", new ArrayList<>(categorySuggestions));

        Map<String, List<String>> recordSuggestions = new HashMap<>();
        recordSuggestions.put("suggest_phrases", abstractPhrases);

        allSuggestions.put("record_suggestions", recordSuggestions);

        return new ResponseEntity<>(allSuggestions, HttpStatus.OK);
    }

    protected List<StacCollectionModel> searchCollectionsByIds(List<String> ids, Boolean isWithGeometry, String sortBy) {

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
                CQLToElasticFilterFactory.getDefaultSetting(),
                queries,
                null,
                filters,
                null,
                createSortOptions(sortBy),
                null);
    }

    @Override
    public List<StacCollectionModel> searchCollectionWithGeometry(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.TRUE, sortBy);
    }

    @Override
    public List<StacCollectionModel> searchAllCollectionsWithGeometry(String sortBy) {
        return searchCollectionsByIds(null, Boolean.TRUE, sortBy);
    }

    @Override
    public List<StacCollectionModel> searchCollections(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.FALSE, sortBy);
    }

    @Override
    public List<StacCollectionModel> searchAllCollections(String sortBy) {
        return searchCollectionsByIds(null, Boolean.FALSE, sortBy);
    }

    @Override
    public List<StacCollectionModel> searchByParameters(List<String> keywords, String cql, List<String> properties, String sortBy, CQLCrsType coor) throws CQLException {

        if((keywords == null || keywords.isEmpty()) && cql == null) {
            return searchAllCollections(sortBy);
        }
        else {

            List<Query> should = null;
            if(keywords != null && !keywords.isEmpty()) {
                should = new ArrayList<>();

                for (String t : keywords) {
                    Query q = MultiMatchQuery.of(m -> m
                            .fuzziness("AUTO")
                            //TODO: what keywords we want to search?
                            .fields(StacBasicField.Title.searchField, StacBasicField.Description.searchField)
                            .prefixLength(0)
                            .query(t))._toQuery();
                    should.add(q);
                }
            }

            List<Query> filters = new ArrayList<>();

            CQLToElasticFilterFactory<CQLCollectionsField> factory = new CQLToElasticFilterFactory<>(coor, CQLCollectionsField.class);
            if(cql != null) {
                Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);

                if(filter instanceof QueryHandler handler) {
                    if(handler.getErrors() == null || handler.getErrors().isEmpty()) {
                        // There is no error during parsing
                        filters = List.of(handler.getQuery());
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

            return searchCollectionBy(
                    factory.getQuerySetting(),
                    null,
                    should,
                    filters,
                    properties,
                    createSortOptions(sortBy),
                    null
            );
        }
    }
    /**
     * Parse and create a sort option
     * https://github.com/opengeospatial/ogcapi-features/blob/0c508be34aaca0d9cf5e05722276a0ee10585d61/extensions/sorting/standard/clause_7_sorting.adoc#L32
     *
     * @param sortBy - Must be of pattern +<property> | -<property>, + mean asc, - mean desc
     * @return
     */
    protected List<SortOptions> createSortOptions(String sortBy) {
        if(sortBy == null || sortBy.isEmpty()) return null;

        String[] args = sortBy.split(",");
        List<SortOptions> sos = new ArrayList<>();

        for(String arg: args) {
            arg = arg.trim();
            if (arg.startsWith("-")) {
                CQLCollectionsField field = Enum.valueOf(CQLCollectionsField.class, arg.substring(1).toLowerCase());

                if(field.getSortField() != null) {
                    sos.add(SortOptions.of(s -> s.field(f -> f.field(field.getSortField()).order(SortOrder.Desc))));
                }
            }
            else {
                // Default is +, there is a catch the + will be replaced as space in the property as + means space in url, by taking
                // default is ASC we work around the problem, the trim removed the space
                CQLCollectionsField field = arg.startsWith("+") ?
                        Enum.valueOf(CQLCollectionsField.class, arg.substring(1).toLowerCase()) :
                        Enum.valueOf(CQLCollectionsField.class, arg.toLowerCase());

                if(field.getSortField() != null) {
                    sos.add(SortOptions.of(s -> s.field(f -> f.field(field.getSortField()).order(SortOrder.Asc))));
                }
            }
        }
        return sos;
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
}
