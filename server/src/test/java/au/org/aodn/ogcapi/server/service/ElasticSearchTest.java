package au.org.aodn.ogcapi.server.service;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ElasticSearchTest {
    private ElasticsearchClient mockClient;

    @BeforeEach
    public void setUp() {
        mockClient = mock(ElasticsearchClient.class);
    }

    @Test
    public void searchByParametersWithDoubleQuote() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                List.of("\"ocean temperature\""),
                null,
                null,
                "-score,-rank",
                CQLCrsType.EPSG4326);

        assertEquals(7, capturingSearch.should.size(),
                "Exact match should produce 7 queries (title + description + other fields, no dataset_group, no fallback)");
        assertTrue(capturingSearch.should.stream().noneMatch(Query::isBool),
                "the all words across fields fallback is not used for a quoted keyword");

        MultiMatchQuery title = capturingSearch.should.get(0).multiMatch();
        assertEquals(TextQueryType.Phrase, title.type(), "Title query should be a phrase multi_match");
        assertEquals(List.of("title^2", "title.synonyms^2"), title.fields());
        assertEquals(0.1, title.tieBreaker());
        assertEquals("ocean temperature", title.query(), "quotes are removed");
        assertNull(title.fuzziness(), "fuzziness is not allowed for a phrase");

        MultiMatchQuery description = capturingSearch.should.get(1).multiMatch();
        assertEquals(TextQueryType.Phrase, description.type(), "Description query should be a phrase multi_match");
        assertEquals(List.of("description", "description.synonyms"), description.fields());
        assertEquals(0.1, description.tieBreaker());

        assertSynonymsOnlyInsideMultiMatch(capturingSearch.should);
        assertTrue(capturingSearch.arguments.sortOptions().get(0).isScript(),
                "dataset_group priority sort should be the first sort key");
    }

    @Test
    public void searchByParametersWithoutDoubleQuote() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                List.of("ocean temperature"),
                null,
                null,
                "-score,-rank",
                CQLCrsType.EPSG4326);

        assertEquals(8, capturingSearch.should.size(), "Fuzzy match should produce 7 queries plus the fallback");

        MultiMatchQuery title = capturingSearch.should.get(0).multiMatch();
        assertEquals(TextQueryType.BestFields, title.type(), "fuzzy_title should be a best_fields multi_match");
        assertEquals(List.of("title^2", "title.synonyms^2"), title.fields());
        assertEquals(0.1, title.tieBreaker());
        assertEquals("AUTO", title.fuzziness());
        assertEquals(4, title.prefixLength());
        assertEquals(Operator.And, title.operator());

        MultiMatchQuery description = capturingSearch.should.get(1).multiMatch();
        assertEquals(TextQueryType.BestFields, description.type(), "fuzzy_desc should be a best_fields multi_match");
        assertEquals(List.of("description", "description.synonyms"), description.fields());
        assertEquals(0.1, description.tieBreaker());
        assertEquals("AUTO", description.fuzziness());
        assertEquals(4, description.prefixLength());
        assertEquals(Operator.And, description.operator());

        assertAllWordsAcrossFieldsFallback(capturingSearch.should.get(7), "ocean temperature",
                capturingSearch.should.subList(0, 7));

        assertSynonymsOnlyInsideMultiMatch(capturingSearch.should);
        assertTrue(capturingSearch.arguments.sortOptions().get(0).isScript(),
                "dataset_group priority sort should be the first sort key");
    }

    @Test
    public void searchByParametersFallbackExcludesOnlyItsOwnKeywordClauses() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                List.of("ocean temperature", "wave"),
                null,
                null,
                "-score,-rank",
                CQLCrsType.EPSG4326);

        assertEquals(16, capturingSearch.should.size(), "8 queries for each of the 2 keywords");
        assertAllWordsAcrossFieldsFallback(capturingSearch.should.get(7), "ocean temperature",
                capturingSearch.should.subList(0, 7));
        assertAllWordsAcrossFieldsFallback(capturingSearch.should.get(15), "wave",
                capturingSearch.should.subList(8, 15));
    }

    /**
     * The fallback must score only through cross_fields over title, description and the vocab fields, and must exclude
     * exactly the records its own keyword clauses match, otherwise the same words are scored twice
     */
    private static void assertAllWordsAcrossFieldsFallback(Query fallback, String keyword, List<Query> keywordClauses) {
        assertTrue(fallback.isBool(), "fallback should be a bool query");
        BoolQuery bool = fallback.bool();
        assertEquals(CapturingElasticSearch.fallbackBoost(), bool.boost());

        assertEquals(1, bool.must().size());
        MultiMatchQuery crossFields = bool.must().get(0).multiMatch();
        assertEquals(TextQueryType.CrossFields, crossFields.type(), "words may come from different fields");
        assertEquals(Operator.And, crossFields.operator(), "every word must be found");
        assertEquals(List.of(
                        "title^2",
                        "description",
                        "summaries.parameter_vocabs",
                        "summaries.platform_vocabs",
                        "summaries.organisation_vocabs"),
                crossFields.fields());
        assertNull(crossFields.fuzziness(), "fuzziness is not allowed for cross_fields");
        assertEquals(0.1, crossFields.tieBreaker(), "best field per word plus 0.1 times the others");
        assertEquals(keyword, crossFields.query());

        assertEquals(keywordClauses.stream().map(Query::toString).toList(),
                bool.mustNot().stream().map(Query::toString).toList(),
                "must_not should hold exactly the clauses of its own keyword");
    }

    /**
     * The synonyms sub-fields must only be scored inside the title/description multi_match, a separate clause on them
     * would score plain words twice. The must_not of the fallback does not score, so only its must is checked.
     */
    private static void assertSynonymsOnlyInsideMultiMatch(List<Query> should) {
        should.stream()
                .filter(q -> !q.isMultiMatch())
                .map(q -> q.isBool() ? q.bool().must().toString() : q.toString())
                .forEach(scored -> assertFalse(scored.contains(".synonyms"),
                        "unexpected clause on a synonyms field: " + scored));
    }

    // The portal SEO pipeline requests these in bulk to build Dataset JSON-LD
    @Test
    public void seoPropertiesMapToStacFields() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                List.of("temperature"),
                null,
                List.of("id", "title", "description", "bbox", "temporal", "themes",
                        "providers", "creation", "revision", "citation", "license"),
                "-score,-rank",
                CQLCrsType.EPSG4326);

        List<String> includes = capturingSearch.normalRequest.source().filter().includes();
        assertTrue(includes.containsAll(List.of(
                "summaries.creation", "summaries.revision", "sci:citation", "license")));
    }

    @Test
    public void emptySearchByParametersMatchesSearchAllCollections() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchAllCollections("-score,-rank");
        SearchArguments searchAllArguments = capturingSearch.arguments;

        capturingSearch.searchByParameters(
                null,
                null,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326);
        SearchArguments emptySearchArguments = capturingSearch.arguments;

        assertEquals(searchAllArguments.queries().toString(), emptySearchArguments.queries().toString());
        assertEquals(searchAllArguments.should(), emptySearchArguments.should());
        assertEquals(searchAllArguments.filters(), emptySearchArguments.filters());
        assertEquals(searchAllArguments.properties(), emptySearchArguments.properties());
        assertEquals(searchAllArguments.searchAfter(), emptySearchArguments.searchAfter());
        assertEquals(searchAllArguments.sortOptions().toString(), emptySearchArguments.sortOptions().toString());
        assertEquals(searchAllArguments.score(), emptySearchArguments.score());
        assertEquals(searchAllArguments.maxSize(), emptySearchArguments.maxSize());
    }

    @Test
    public void explainByParametersUsesScriptScoreRequestForKeywords() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        JsonNode result = capturingSearch.explainByParameters(
                List.of("ocean temperature"),
                null,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326,
                false);

        assertEquals("captured", result.path("status").asText());
        assertEquals(10000, capturingSearch.explainRequest.size(),
                "title-only _source is lightweight so the larger search_after batch is used");
        assertNotNull(capturingSearch.explainRequest.query());
        assertTrue(capturingSearch.explainRequest.query().isScriptScore());
        assertEquals(8, capturingSearch.explainRequest.query().scriptScore()
                .query().bool().should().size());
        assertNotNull(capturingSearch.explainRequest.source());
        assertTrue(capturingSearch.explainRequest.source().isFilter());
        assertFalse(capturingSearch.explainRequest.source().filter().includes().isEmpty());
    }

    @Test
    public void explainByParametersUsesCollectionQueryForEmptySearch() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.explainByParameters(
                null,
                null,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326,
                false);

        SearchRequest request = capturingSearch.explainRequest;
        assertEquals(100, request.size());
        assertNotNull(request.query());
        assertTrue(request.query().isBool());
        assertEquals("type", request.query().bool().must().get(0).match().field());
        assertEquals("Collection", request.query().bool().must().get(0).match().query().stringValue());
        assertNotNull(request.source());
        assertTrue(request.source().isFetch());
        assertTrue(request.source().fetch(),
                "Empty parameter searches must preserve searchAllCollections source behavior");
    }

    @Test
    public void normalAndExplainRequestsMatchForKeywordAndCqlSettings() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);
        List<String> keywords = List.of("temperature");
        String cql = "parameter_vocabs='temperature' AND page_size=3 AND score>=1.3";

        capturingSearch.searchByParameters(
                keywords,
                cql,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326);
        SearchRequest normalRequest = capturingSearch.normalRequest;

        capturingSearch.explainByParameters(
                keywords,
                cql,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326,
                false);
        SearchRequest explainRequest = capturingSearch.explainRequest;

        assertEquals(normalRequest.toString(), explainRequest.toString());
        assertEquals(3, explainRequest.size());
        assertEquals(1.3, explainRequest.minScore());
        assertNotNull(explainRequest.query());
        assertTrue(explainRequest.query().isScriptScore());
        assertFalse(explainRequest.query().scriptScore().query().bool().filter().isEmpty());
    }

    @Test
    public void lightweightPropertiesUseLargerSearchPageSize() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                null,
                "temporal AFTER 1970-01-01T00:00:00Z",
                List.of("id", "temporal"),
                "id",
                CQLCrsType.EPSG4326);

        assertEquals(10000, capturingSearch.normalRequest.size(),
                "id,temporal is a small _source so the catalog query can fetch 10000 hits per page");

        capturingSearch.searchByParameters(
                null,
                "temporal AFTER 1970-01-01T00:00:00Z",
                List.of("id", "providers"),
                "id",
                CQLCrsType.EPSG4326);

        assertEquals(10000, capturingSearch.normalRequest.size(),
                "id,providers is also a small _source");
    }

    @Test
    public void heavyOrMissingPropertiesKeepDefaultSearchPageSize() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                null,
                "temporal AFTER 1970-01-01T00:00:00Z",
                List.of("id", "centroid"),
                "id",
                CQLCrsType.EPSG4326);
        assertEquals(100, capturingSearch.normalRequest.size(),
                "centroid pulls no-land geometry so the conservative batch size is kept");

        capturingSearch.searchByParameters(
                null,
                "temporal AFTER 1970-01-01T00:00:00Z",
                List.of("id", "links"),
                "id",
                CQLCrsType.EPSG4326);
        assertEquals(100, capturingSearch.normalRequest.size(),
                "links payloads stay on the conservative batch size");

        capturingSearch.searchAllCollections("id");
        assertEquals(100, capturingSearch.normalRequest.size(),
                "full-document searches keep the conservative batch size");
    }

    @Test
    public void cqlPageSizeStillCapsLightweightSearch() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                null,
                "temporal AFTER 1970-01-01T00:00:00Z AND page_size=3",
                List.of("id", "temporal"),
                "id",
                CQLCrsType.EPSG4326);

        assertEquals(3, capturingSearch.normalRequest.size(),
                "CQL page_size remains the upper bound even for lightweight property lists");
    }

    @Test
    public void cqlPreferenceIsSetOnNormalAndExplainRequests() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);
        List<String> keywords = List.of("temperature");
        String cql = "preference='search-ranking-test' AND page_size=3";

        capturingSearch.searchByParameters(
                keywords,
                cql,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326);
        SearchRequest normalRequest = capturingSearch.normalRequest;

        capturingSearch.explainByParameters(
                keywords,
                cql,
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326,
                false);
        SearchRequest explainRequest = capturingSearch.explainRequest;

        assertEquals("search-ranking-test", normalRequest.preference(),
                "the same preference routes identical searches to the same shard copies");
        assertEquals(normalRequest.toString(), explainRequest.toString(),
                "explain must be routed the same way as the search it explains");
        assertTrue(normalRequest.query().scriptScore().query().bool().filter().isEmpty(),
                "preference is a setting, not a filter");
    }

    @Test
    public void noPreferenceUnlessCqlSetsIt() throws Exception {
        CapturingElasticSearch capturingSearch = new CapturingElasticSearch(mockClient);

        capturingSearch.searchByParameters(
                List.of("temperature"),
                "page_size=3",
                List.of("title"),
                "-score,-rank",
                CQLCrsType.EPSG4326);

        assertNull(capturingSearch.normalRequest.preference(),
                "routing is left to Elasticsearch when the caller does not opt in");
    }

    private record SearchArguments(
            List<Query> queries,
            List<Query> should,
            List<Query> filters,
            List<String> properties,
            List<FieldValue> searchAfter,
            List<SortOptions> sortOptions,
            Double score,
            Long maxSize,
            String preference) {
    }

    private static class CapturingElasticSearch extends ElasticSearch {
        private List<Query> should;
        private SearchArguments arguments;
        private SearchRequest normalRequest;
        private SearchRequest explainRequest;

        private CapturingElasticSearch(ElasticsearchClient client) {
            super(client, null, new ObjectMapper(), "test-index", 100, 10000, 10);
            this.searchAfterSplitRegex = "\\|\\|";
        }

        private static float fallbackBoost() {
            return ALL_WORDS_ACROSS_FIELDS_BOOST;
        }

        @Override
        protected SearchResult<au.org.aodn.stac.model.StacCollectionModel> searchCollectionBy(
                List<Query> queries,
                List<Query> should,
                List<Query> filters,
                List<String> properties,
                List<FieldValue> searchAfter,
                List<SortOptions> sortOptions,
                Double score,
                Long maxSize,
                String preference) {
            this.should = should;
            this.arguments = new SearchArguments(
                    queries,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    sortOptions,
                    score,
                    maxSize,
                    preference);
            this.normalRequest = buildCollectionSearchRequestSupplier(
                    queries,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    sortOptions,
                    score,
                    maxSize,
                    preference).get().build();
            SearchResult<au.org.aodn.stac.model.StacCollectionModel> result = new SearchResult<>();
            result.setCollections(List.of());
            return result;
        }

        @Override
        protected JsonNode explainCollectionBy(Supplier<SearchRequest.Builder> requestSupplier, boolean simplified) {
            this.explainRequest = requestSupplier.get().build();
            ObjectNode result = mapper.createObjectNode();
            result.put("status", "captured");
            return result;
        }
    }
}
