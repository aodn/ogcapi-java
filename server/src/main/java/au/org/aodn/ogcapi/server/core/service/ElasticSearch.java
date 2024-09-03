package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.dto.SearchSuggestionsDto;
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
import co.elastic.clients.util.ObjectBuilder;
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

    @Value("${elasticsearch.search_as_you_type.search_suggestions.path}")
    protected String searchAsYouTypeFieldsPath;

    @Value("${elasticsearch.search_as_you_type.search_suggestions.fields}")
    protected String[] searchAsYouTypeEnabledFields;

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

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

    protected List<Hit<SearchSuggestionsDto>> getSuggestionsByField(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
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
        SearchResponse<SearchSuggestionsDto> response = esClient.search(searchRequest, SearchSuggestionsDto.class);
        log.info("getRecordSuggestions | Elastic search response {}", response);

        // return
        return response.hits().hits();
    }

    public ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        Map<String, Set<String>> searchSuggestions = new HashMap<>();

        // extract parameter vocab suggestions
        Set<String> parameterVocabSuggestions = this.getSuggestionsByField(input, cql, coor)
                .stream()
                .filter(item -> item.source() != null && item.source().getParameterVocabs() != null && !item.source().getParameterVocabs().isEmpty())
                .flatMap(item -> item.source().getParameterVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_parameter_vocabs", parameterVocabSuggestions);

        Set<String> platformVocabSuggestions = this.getSuggestionsByField(input, cql, coor)
                .stream()
                .filter(item -> item.source() != null && item.source().getPlatformVocabs() != null && !item.source().getPlatformVocabs().isEmpty())
                .flatMap(item -> item.source().getPlatformVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_platform_vocabs", platformVocabSuggestions);

        Set<String> organisationVocabSuggestions = this.getSuggestionsByField(input, cql, coor)
                .stream()
                .filter(item -> item.source() != null && item.source().getOrganisationVocabs() != null && !item.source().getOrganisationVocabs().isEmpty())
                .flatMap(item -> item.source().getOrganisationVocabs().stream())
                .filter(vocab -> vocab.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_organisation_vocabs", organisationVocabSuggestions);

        // extract abstract phrases suggestions
        Set<String> abstractPhrases = this.getSuggestionsByField(input, cql, coor)
                .stream()
                .filter(item -> item.source() != null && item.source().getAbstractPhrases() != null && !item.source().getAbstractPhrases().isEmpty())
                .flatMap(item -> item.source().getAbstractPhrases().stream())
                .filter(phrase -> phrase.toLowerCase().contains(input.toLowerCase()))
                .collect(Collectors.toSet());
        searchSuggestions.put("suggested_phrases", abstractPhrases);

        return new ResponseEntity<>(searchSuggestions, HttpStatus.OK);
    }

    protected ElasticSearchBase.SearchResult searchCollectionsByIds(List<String> ids, Boolean isWithGeometry, String sortBy) {

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
                createSortOptions(sortBy),
                null,
                null);
    }

    @Override
    public ElasticSearchBase.SearchResult searchCollectionWithGeometry(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.TRUE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult searchAllCollectionsWithGeometry(String sortBy) {
        return searchCollectionsByIds(null, Boolean.TRUE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult searchCollections(List<String> ids, String sortBy) {
        return searchCollectionsByIds(ids, Boolean.FALSE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult searchAllCollections(String sortBy) {
        return searchCollectionsByIds(null, Boolean.FALSE, sortBy);
    }

    @Override
    public ElasticSearchBase.SearchResult searchByParameters(List<String> keywords, String cql, List<String> properties, String sortBy, CQLCrsType coor) throws CQLException {

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
                            .prefixLength(3)
                            .query(t))._toQuery();
                    should.add(q);
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
            Double score = null;
            try {
                if (setting.get(CQLElasticSetting.score) != null &&
                        !setting.get(CQLElasticSetting.score).isBlank()) {
                    score = Double.parseDouble(setting.get(CQLElasticSetting.score));
                }
            }
            catch(Exception e) {
                // OK to ignore as accept null as the value
            }
            // Get the search after
            List<FieldValue> searchAfter = null;
            if (setting.get(CQLElasticSetting.search_after) != null &&
                    !setting.get(CQLElasticSetting.search_after).isBlank()) {
                // Convert the comma separate string to List<FieldValue>
                searchAfter = Arrays.stream(setting.get(CQLElasticSetting.search_after)
                        .split(","))
                        .filter(v -> !v.isBlank())
                        .map(ElasticSearch::toFieldValue)
                        .toList();
            }

            return searchCollectionBy(
                    null,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    createSortOptions(sortBy),
                    score,
                    maxSize
            );
        }
    }
    /**
     * Parse and create a sort option
     * <a href="https://github.com/opengeospatial/ogcapi-features/blob/0c508be34aaca0d9cf5e05722276a0ee10585d61/extensions/sorting/standard/clause_7_sorting.adoc#L32">...</a>
     *
     * @param sortBy - Must be of pattern +<property> | -<property>, + mean asc, - mean desc
     * @return List of sort options
     */
    protected List<SortOptions> createSortOptions(String sortBy) {
        if(sortBy == null || sortBy.isEmpty()) return null;

        String[] args = sortBy.split(",");
        List<SortOptions> sos = new ArrayList<>();

        for(String arg: args) {
            arg = arg.trim();
            if (arg.startsWith("-")) {
                CQLFields field = Enum.valueOf(CQLFields.class, arg.substring(1).toLowerCase());

                if(field.getSortBuilder() != null) {
                    ObjectBuilder<SortOptions> sb = field.getSortBuilder().apply(SortOrder.Desc);
                    sos.add(sb.build());
                }
            }
            else {
                // Default is +, there is a catch the + will be replaced as space in the property as + means space in url, by taking
                // default is ASC we work around the problem, the trim removed the space
                CQLFields field = arg.startsWith("+") ?
                        Enum.valueOf(CQLFields.class, arg.substring(1).toLowerCase()) :
                        Enum.valueOf(CQLFields.class, arg.toLowerCase());

                if(field.getSortBuilder() != null) {
                    ObjectBuilder<SortOptions> sb = field.getSortBuilder().apply(SortOrder.Asc);
                    sos.add(sb.build());
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
        // Assume it is string
        return FieldValue.of(s.trim());
    }
}
