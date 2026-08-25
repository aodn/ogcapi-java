package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.stac.model.SearchSuggestionsModel;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.elastic.CQLToElasticFilterFactory;
import au.org.aodn.ogcapi.server.core.parser.elastic.QueryHandler;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlighterOrder;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.Supplier;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.ELASTIC_SEARCH_UUID_ONLY;

@Slf4j
public class ElasticSearch extends ElasticSearchBase implements Search {

    protected Map<CQLElasticSetting, String> defaultElasticSetting;

    // the semantic_text field on the vocabs index
    protected static final String SEMANTIC_CONCEPT_FIELD = "concept_semantic";

    // organisation vocabs are skipped from sementic query
    protected static final String ORGANISATION_VOCAB_FIELD = "organisation_vocab";

    // a vocabs doc holds exactly one of these
    protected static final List<String> VOCAB_TYPES =
            List.of("parameter_vocab", "platform_vocab", ORGANISATION_VOCAB_FIELD);

    @Value("${elasticsearch.search_as_you_type.search_suggestions.path}")
    protected String searchAsYouTypeFieldsPath;

    @Value("${elasticsearch.search_as_you_type.search_suggestions.fields}")
    protected String[] searchAsYouTypeEnabledFields;

    @Value("${elasticsearch.search_after.split_regex:\\|\\|}")
    protected String searchAfterSplitRegex;

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

    @Value("${elasticsearch.semantic.enabled:false}")
    protected Boolean semanticEnabled;

    @Value("${elasticsearch.semantic.size:3}")
    protected Integer semanticSize;

    @Value("${elasticsearch.semantic.min_input_length:3}")
    protected Integer semanticMinInputLength;

    @Value("${elasticsearch.semantic.fragments:3}")
    protected Integer semanticFragments;

    @Value("${elasticsearch.semantic.max_suggestions:5}")
    protected Integer semanticMaxSuggestions;

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
    /**
     * TODO: need to observe the behaviour of different types and pick the best one for our needs,
     * phrase_prefix type produces the most similar effect to the completion suggester but ElasticSearch says it is not the best choice:
     *   > To search for documents that strictly match the query terms in order, or to search using other properties of phrase queries, use a match_phrase_prefix query on the root field.
     *   > A match_phrase query can also be used if the last term should be matched exactly, and not as a prefix. Using phrase queries may be less efficient than using the match_bool_prefix query.
     * ElasticSearch recommends using bool_prefix type: <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-as-you-type.html">...</a>
     *   > The most efficient way of querying to serve a search-as-you-type use case is usually a multi_match query of type bool_prefix that targets the root search_as_you_type field and its shingle subfields.
     *   > This can match the query terms in any order, but will score documents higher if they contain the terms in order in a shingle subfield.
     * Also, if using phrase_prefix, it is not allowed to use fuzziness parameter:
     *   > Fuzziness not allowed for type [phrase_prefix]
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-as-you-type.html#specific-params">...</a>
     *
     * @param input - The input text
     * @param suggestField - The field name that you want to search
     */
    protected Query generateSearchAsYouTypeQuery(String input, String suggestField) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.multiMatch(mm -> mm
                        .query(input)
                        .type(TextQueryType.BoolPrefix)
                        .fuzziness("AUTO")
                        .fields(Arrays.asList(
                                suggestField + "^10",         // 1. Root field is most relevant
                                suggestField + "._2gram^5",   // 2. Pair matches (shingles)
                                suggestField + "._3gram^2"    // 3. Triple matches
                        ))
                ))
                // 4. Add a "Phrase" boost: If they type words in the exact order,
                // give it an extra push to the top.
                .should(s -> s.matchPhrasePrefix(mpp -> mpp
                        .field(suggestField)
                        .query(input)
                        .boost(15f)
                ))
        ));
    }

    protected List<Hit<SearchSuggestionsModel>> getSuggestionsByField(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        // 1. Map fields directly to Queries and collect to a List
        List<Query> suggestFieldsQueries = Stream.of(searchAsYouTypeEnabledFields)
                .map(field -> generateSearchAsYouTypeQuery(input, searchAsYouTypeFieldsPath + "." + field))
                .toList();

        Query searchAsYouTypeQuery = Query.of(q -> q.nested(n -> n
                .path(searchAsYouTypeFieldsPath)
                .query(bQ -> bQ.bool(b -> b.should(suggestFieldsQueries)))
        ));

        List<Query> filters = buildSuggestionFilters(cql, coor);

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

    /*
        this is where the discovery parameter vocabs filter is applied
        use term query for exact match of the parameter vocabs
        (e.g you don't want "something", "something special" and "something secret" be returned when searching for "something")
        see more: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-terms-query.html#query-dsl-terms-query
        this query uses AND operator for the parameter vocabs (e.g "wave" AND "temperature")
    */
    protected List<Query> buildSuggestionFilters(String cql, CQLCrsType coor) throws CQLException {
        if (cql != null) {
            CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(coor, CQLFields.class);
            Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);
            if (filter instanceof QueryHandler elasticFilter) {
                return List.of(elasticFilter.getQuery());
            }
        }
        // If no filter, then use the match_all{} to get all record
        return List.of(MatchAllQuery.of(q -> q)._toQuery());
    }

    /**
     * Only conduct semantic search if the input is long enough
     * */
    protected boolean isSemanticInputLongEnough(String input) {
        return input != null && input.trim().length() >= semanticMinInputLength;
    }

    /**
     * Rank vocab terms by meaning similarity with query. Comparing with documents in vocabs index with the semantic_text field "concept_semantic",
     * which is a list of combined text for per concepts (level-2 label) as "level-2 label's title. level-2 label's description. leaf labels' title".
     * Using highlight option to get the real matched level-2 label.
     * @param input - The input text typed by the end user
     */
    protected List<Hit<JsonNode>> getSemanticTermHits(String input) throws IOException {
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(vocabsIndexName)
                .size(semanticSize)
                .query(q -> q.bool(b -> b
                        .must(m -> m.semantic(sm -> sm
                                .field(SEMANTIC_CONCEPT_FIELD)
                                .query(input)))
                        .mustNot(mn -> mn.exists(e -> e.field(ORGANISATION_VOCAB_FIELD)))))
                .highlight(h -> h
                        .fields(SEMANTIC_CONCEPT_FIELD, f -> f
                                .numberOfFragments(semanticFragments)
                                // The highlighter picks the top fragments by score but hands them back in field order unless asked otherwise,
                                // so add highligherorder to makesure the fragment is ordered by score.
                                .order(HighlighterOrder.Score)
                                .preTags("")
                                .postTags(""))));

        log.info("getSemanticTermHits | Elastic search payload {}", searchRequest);
        SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
        log.info("getSemanticTermHits | Elastic search response {}", response);

        return response.hits().hits();
    }

    /**
     * A vocabs doc holds exactly one of the three concept types (see es-indexer VocabDto), so the first one present is the one to label.
     * `display_label` is the human-facing form and matches what a record's summaries.*_vocabs contain; If it's empty return `label`.
     */
    protected String extractLabel(JsonNode source) {
        if (source == null) {
            return null;
        }
        for (String type : VOCAB_TYPES) {
            JsonNode vocab = source.get(type);
            if (vocab != null) {
                JsonNode displayLabel = vocab.get("display_label");
                if (displayLabel != null && !displayLabel.asText().isBlank()) {
                    return displayLabel.asText();
                }
                JsonNode label = vocab.get("label");
                if (label != null && !label.asText().isBlank()) {
                    return label.asText();
                }
            }
        }
        return null;
    }

    /**
     * concept_semantic holds one entry per narrower (level-2) concept, each starting with that concept's label (es-indexer VocabDto.getConceptSemantic).
     * The semantic highlighter returns the matching entries ranked by score, so a fragment's leading segment names the concept that actually matched.
     */
    protected List<String> extractSemanticLabels(Hit<JsonNode> hit) {
        List<String> fragments = hit.highlight() == null
                ? null
                : hit.highlight().get(SEMANTIC_CONCEPT_FIELD);

        if (fragments == null || fragments.isEmpty()) {
            // No highlight - e.g. an index still carrying the old single-valued concept_semantic.
            String label = extractLabel(hit.source());
            return label == null ? List.of() : List.of(label);
        }
        return fragments.stream()
                .map(this::toConceptLabel)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Flatten the per-document concept labels round-robin: every document's best concept, then every document's second best, and so on.
     * Determined by semantic.size (number of documents should return) and semantic.fragments (number of best concpets should return for each documents)
     *
     * @param labelsPerDoc - concept labels per hit, hits in _score order, labels in fragment order
     */
    protected List<String> interleave(List<List<String>> labelsPerDoc) {
        int deepest = labelsPerDoc.stream().mapToInt(List::size).max().orElse(0);

        List<String> flattened = new ArrayList<>();
        for (int rank = 0; rank < deepest; rank++) {
            for (List<String> labels : labelsPerDoc) {
                // A document that ran out of concepts simply stops contributing at this rank.
                if (rank < labels.size()) {
                    flattened.add(labels.get(rank));
                }
            }
        }
        return flattened;
    }

    /**
     * Leading segment of a concept_semantic entry, which is the concept's label. Split on ". "
     * rather than "." so labels that carry an internal period (e.g. "No.3 buoy") survive.
     */
    protected String toConceptLabel(String fragment) {
        if (fragment == null) {
            return null;
        }
        // -1 means the fragment is a single segment and is the label; 0 means it opens with the
        // separator, leaving no label at all - the two must not collapse into the same branch.
        int end = fragment.indexOf(". ");
        String label = (end >= 0 ? fragment.substring(0, end) : fragment).trim();
        return label.isBlank() ? null : label;
    }

    public ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws IOException, CQLException {
        Map<String, Object> searchSuggestions = new HashMap<>();
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

        // Semantic suggestions - vocab terms ranked by meaning rather than by spelling.
        if (Boolean.TRUE.equals(semanticEnabled) && isSemanticInputLongEnough(input)) {
            try {
                List<Hit<JsonNode>> semanticHits = this.getSemanticTermHits(input);

                List<List<String>> labelsPerDoc = semanticHits
                        .stream()
                        .map(this::extractSemanticLabels)
                        .toList();

                Set<String> semanticSuggestions = interleave(labelsPerDoc)
                        .stream()
                        // distinct before limit so duplicates do not consume suggestion slots
                        .distinct()
                        .limit(semanticMaxSuggestions)
                        // LinkedHashSet so the relevance order from Elastic survives into the response
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                searchSuggestions.put("suggested_semantic", semanticSuggestions);
            } catch (Exception e) {
                // Covers the case where the index was built without the semantic fields - the
                // dropdown degrades to lexical suggestions rather than the request failing.
                log.warn("Semantic suggestions unavailable, returning lexical suggestions only", e);
            }
        }

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


    /**
     * Builds the relevance should clauses for each search keyword. Shared by searchByParameters (search) and buildParameterSearchRequestSupplier (explain) so the two align with each other.
     * These should clauses contribute to the Elasticsearch BM25 relevance score.
     */
    private static List<Query> createKeywordShouldClauses(List<String> keywords) {
        List<Query> should = new ArrayList<>();
        for (String t : keywords) {
            // If user's input (keywords) wrapped with double quot", and the text is not empty, treat the user intend to search with the exact term, so fuzzy matching not applied on title and description
            boolean isExact = t.startsWith("\"") && t.endsWith("\"") && t.length() > 2;
            // If search text with double quote, remove quotee, otherwise keeps same
            String term = isExact ? t.substring(1, t.length() - 1) : t;

            if (isExact) {
                // Match phrase in original title and description, not use fuzzy fields
                should.add(CQLFields.title.getPropertyEqualToQuery(term));
                should.add(CQLFields.description.getPropertyEqualToQuery(term));
            }
            else {
                should.add(CQLFields.fuzzy_title.getPropertyEqualToQuery(term));
                should.add(CQLFields.fuzzy_desc.getPropertyEqualToQuery(term));
            }
            should.add(CQLFields.parameter_vocabs.getPropertyEqualToQuery(term));
            should.add(CQLFields.organisation_vocabs.getPropertyEqualToQuery(term));
            should.add(CQLFields.platform_vocabs.getPropertyEqualToQuery(term));
            should.add(CQLFields.id.getPropertyEqualToQuery(term));
            // Acronym match on the *.synonyms sub-fields, e.g. "SOOP" -> "ships of opportunity".
            should.add(CQLFields.acronym_title.getPropertyEqualToQuery(term));
            should.add(CQLFields.acronym_desc.getPropertyEqualToQuery(term));
            // credit_contains uses match query by default, exact match is not applied here
            should.add(CQLFields.credit_contains.getPropertyEqualToQuery(term));
        }
        return should;
    }

    /**
     * Dataset-group candidate values for the priority sort, expanded from the search keywords with the same exact/quoted handling as the should clauses.
     */
    private static List<String> collectDatasetGroupCandidates(List<String> keywords) {
        return keywords.stream()
                .map(t -> {
                    boolean isExact = t.startsWith("\"") && t.endsWith("\"") && t.length() > 2;
                    String term = isExact ? t.substring(1, t.length() - 1) : t;
                    return CQLFields.getDatasetGroupCandidates(term, isExact);
                })
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    /**
     * Build SearchRequest for searchByParameters and explainByParameters
     * */
    protected Supplier<SearchRequest.Builder> buildParameterSearchRequestSupplier(
            List<String> keywords,
            String cql,
            List<String> properties,
            String sortBy,
            CQLCrsType coor) throws CQLException {

        if ((keywords == null || keywords.isEmpty()) && cql == null) {
            List<Query> queries = new ArrayList<>();
            queries.add(MatchQuery.of(m -> m
                    .field(StacType.searchField)
                    .query(StacType.Collection.value))._toQuery());

            return buildCollectionSearchRequestSupplier(
                    queries,
                    null,
                    null,
                    null,
                    null,
                    createSortOptions(sortBy, CQLFields.class),
                    null,
                    null);
        }

        List<Query> should = null;
        List<String> datasetGroupCandidates = List.of();
        if (keywords != null && !keywords.isEmpty()) {
            should = createKeywordShouldClauses(keywords);
            datasetGroupCandidates = collectDatasetGroupCandidates(keywords);
        }

        List<Query> filters = new ArrayList<>();
        CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(coor, CQLFields.class);

        if (cql != null) {
            Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);
            if (filter instanceof QueryHandler handler) {
                if (handler.getErrors() == null || handler.getErrors().isEmpty()) {
                    if (handler.getQuery() != null) {
                        filters = List.of(handler.getQuery());
                    }
                }
                else {
                    throw new IllegalArgumentException("ECQL Parse Error");
                }
            }
        }

        Map<CQLElasticSetting, String> setting = factory.getQuerySetting();

        Long maxSize = setting.get(CQLElasticSetting.page_size) == null
                || setting.get(CQLElasticSetting.page_size).isBlank()
                ? null
                : Long.parseLong(setting.get(CQLElasticSetting.page_size));

        Double score = setting.get(CQLElasticSetting.score) == null
                || setting.get(CQLElasticSetting.score).isBlank()
                ? null
                : Double.parseDouble(setting.get(CQLElasticSetting.score));

        List<FieldValue> searchAfter = null;
        if (setting.get(CQLElasticSetting.search_after) != null
                && !setting.get(CQLElasticSetting.search_after).isBlank()) {
            searchAfter = Arrays.stream(setting.get(CQLElasticSetting.search_after).split(searchAfterSplitRegex))
                    .filter(v -> !v.isBlank())
                    .map(String::trim)
                    .map(ElasticSearch::toFieldValue)
                    .toList();
        }

        List<SortOptions> sortOptions = createSortOptions(sortBy, CQLFields.class);

        if (factory.isParameterPrioritySort()) {
            if (sortOptions == null) sortOptions = new ArrayList<>();
            sortOptions.add(0, CQLFields.parameter_vocabs.getSortBuilder().apply(SortOrder.Desc).build());
        }

        if (factory.isPlatformPrioritySort()) {
            if (sortOptions == null) sortOptions = new ArrayList<>();
            sortOptions.add(0, CQLFields.platform_vocabs.getSortBuilder().apply(SortOrder.Desc).build());
        }

        // Records whose dataset_group matches a search term rank first among recalled records; prepended last so it is the strongest sort key.
        if (should != null && !should.isEmpty() && !datasetGroupCandidates.isEmpty()) {
            if (sortOptions == null) sortOptions = new ArrayList<>();
            sortOptions.add(0, CQLFields.getDatasetGroupPrioritySort(datasetGroupCandidates));
        }

        return buildCollectionSearchRequestSupplier(
                null,
                should,
                filters,
                properties,
                searchAfter,
                sortOptions,
                score,
                maxSize);
    }

    @Override
    public ElasticSearchBase.SearchResult<StacCollectionModel> searchByParameters(List<String> keywords, String cql, List<String> properties, String sortBy, CQLCrsType coor) throws CQLException {

        if((keywords == null || keywords.isEmpty()) && cql == null) {
            return searchAllCollections(sortBy);
        }
        else {

            List<Query> should = null;
            List<String> datasetGroupCandidates = List.of();
            if(keywords != null && !keywords.isEmpty()) {
                should = createKeywordShouldClauses(keywords);
                datasetGroupCandidates = collectDatasetGroupCandidates(keywords);
            }

            List<Query> filters = new ArrayList<>();

            CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(coor, CQLFields.class);
            if(cql != null) {
                try {
                    Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);
                    if(filter instanceof QueryHandler handler) {
                        if(handler.getErrors() == null || handler.getErrors().isEmpty()) {
                            if(handler.getQuery() != null) {
                                // There is no error during parsing
                                filters = List.of(handler.getQuery());
                            }
                        }
                        else {
                            throw new IllegalArgumentException(
                                    "ECQL Parse Error",
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
                catch(CQLException ce) {
                    log.error("Error parsing ECQL", ce);
                    throw ce;
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

            List<SortOptions> sortOptions = createSortOptions(sortBy, CQLFields.class);
            // When the filter searches curated vocab fields, prepend presence-based priority sort keys
            // so matching human-curated records rank above AI-generated fallback records. This is
            // the first sort key; existing -score,-rank ordering is preserved within each tier.
            if (factory.isParameterPrioritySort()) {
                if (sortOptions == null) {
                    sortOptions = new ArrayList<>();
                }
                sortOptions.add(0, CQLFields.parameter_vocabs.getSortBuilder().apply(SortOrder.Desc).build());
            }
            if (factory.isPlatformPrioritySort()) {
                if (sortOptions == null) {
                    sortOptions = new ArrayList<>();
                }
                sortOptions.add(0, CQLFields.platform_vocabs.getSortBuilder().apply(SortOrder.Desc).build());
            }

            // Records whose dataset_group matches a search term rank first among recalled records; prepended last so it is the strongest sort key.
            if (should != null && !should.isEmpty() && !datasetGroupCandidates.isEmpty()) {
                if (sortOptions == null) {
                    sortOptions = new ArrayList<>();
                }
                sortOptions.add(0, CQLFields.getDatasetGroupPrioritySort(datasetGroupCandidates));
            }

            return searchCollectionBy(
                    null,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    sortOptions,
                    score,
                    maxSize
            );
        }
    }

    @Override
    public JsonNode explainByParameters(List<String> targets, String filter, List<String> properties, String sortBy, CQLCrsType coor, boolean isSimplified) throws Exception {
        return explainCollectionBy(
                buildParameterSearchRequestSupplier(targets, filter, properties, sortBy, coor),
                isSimplified);
    }

    @Override
    public JsonNode explainByUuid(String uuid, List<String> targets, String filter, List<String> properties, String sortBy, CQLCrsType coor) throws Exception {
        return explainCollectionById(
                uuid,
                buildParameterSearchRequestSupplier(targets, filter, properties, sortBy, coor));
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
        // Assume it is a string
        return FieldValue.of(s.trim());
    }
}
