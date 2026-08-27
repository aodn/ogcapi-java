package au.org.aodn.ogcapi.server.service;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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

        assertEquals(10, capturingSearch.should.size(),
                "Exact match should produce 10 queries (title + description + other fields)");
        assertTrue(capturingSearch.should.get(0).isMatchPhrase(), "Title query should be MatchPhraseQuery");
        assertTrue(capturingSearch.should.get(1).isMatchPhrase(), "Description query should be MatchPhraseQuery");
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

        assertEquals(10, capturingSearch.should.size(), "Fuzzy match should produce 10 queries");
        assertTrue(capturingSearch.should.get(0).isMatch(), "fuzzy_title should be MatchQuery");
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
        assertEquals(100, capturingSearch.explainRequest.size());
        assertNotNull(capturingSearch.explainRequest.query());
        assertTrue(capturingSearch.explainRequest.query().isScriptScore());
        assertEquals(10, capturingSearch.explainRequest.query().scriptScore()
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

    private record SearchArguments(
            List<Query> queries,
            List<Query> should,
            List<Query> filters,
            List<String> properties,
            List<FieldValue> searchAfter,
            List<SortOptions> sortOptions,
            Double score,
            Long maxSize) {
    }

    private static class CapturingElasticSearch extends ElasticSearch {
        private List<Query> should;
        private SearchArguments arguments;
        private SearchRequest normalRequest;
        private SearchRequest explainRequest;

        private CapturingElasticSearch(ElasticsearchClient client) {
            super(client, null, new ObjectMapper(), "test-index", 100, 10);
            this.searchAfterSplitRegex = "\\|\\|";
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
                Long maxSize) {
            this.should = should;
            this.arguments = new SearchArguments(
                    queries,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    sortOptions,
                    score,
                    maxSize);
            this.normalRequest = buildCollectionSearchRequestSupplier(
                    queries,
                    should,
                    filters,
                    properties,
                    searchAfter,
                    sortOptions,
                    score,
                    maxSize).get().build();
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
