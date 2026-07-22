package au.org.aodn.ogcapi.server.service;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.server.core.model.EsFeatureCollectionModel;
import au.org.aodn.ogcapi.server.core.model.EsFeatureModel;
import au.org.aodn.ogcapi.server.core.model.EsPolygonModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ElasticSearchTest {
    private ElasticsearchClient mockClient;
    private ElasticSearch elasticSearch;

    @BeforeEach
    public void setUp() {
        mockClient = mock(ElasticsearchClient.class);
        elasticSearch = new ElasticSearch(
                mockClient,
                null, // CacheNoLandGeometry
                new ObjectMapper(),
                "test-index",
                100,
                10
        );
    }

    @Test
    public void searchFeatureSummaryTest() throws IOException {

        // Arrange
        String collectionId = "test-collection";
        List<String> properties = List.of("*");
        String filter = null;

        SearchResponse<EsFeatureCollectionModel> mockResponse = mock(SearchResponse.class);
        Hit<EsFeatureCollectionModel> hit = mock(Hit.class);
        var esFeatureCollection = new EsFeatureCollectionModel();
        Map<String, Object> featureCollectionProperties = new HashMap<>();
        featureCollectionProperties.put("date", "2004-01");
        featureCollectionProperties.put("collection", "2d496463-600c-465a-84a1-8a4ab76bd505");
        featureCollectionProperties.put("key", "satellite_ghrsst_l4_gamssa_1day_multi_sensor_world.zarr");
        esFeatureCollection.setProperties(featureCollectionProperties);
        List<List<List<BigDecimal>>> coords = new ArrayList<>();
        var esFeature = new EsFeatureModel();

        // mock the feature properties
        Map<String, Object> featureProperties = new HashMap<>();
        featureProperties.put("date", "1939-09");
        featureProperties.put("count", 5);
        esFeature.setProperties(featureProperties);

        // mock a single point [147.338884, -43.190779]
        List<List<BigDecimal>> ring = new ArrayList<>();
        List<BigDecimal> point = List.of(
                new BigDecimal("147.338884"),
                new BigDecimal("-43.190779")
        );
        ring.add(point);
        coords.add(ring);

        var polygon = new EsPolygonModel();
        polygon.setCoordinates(coords);

        esFeature.setGeometry(polygon);

        esFeatureCollection.setFeatures(List.of(esFeature));

        when(hit.source()).thenReturn(esFeatureCollection);

        when(mockClient.search(any(SearchRequest.class), eq(EsFeatureCollectionModel.class)))
                .thenReturn(mockResponse);

        HitsMetadata<EsFeatureCollectionModel> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(1L);
        when(hitsMetadata.total()).thenReturn(totalHits);

        when(mockResponse.hits()).thenReturn(hitsMetadata);

        when(mockClient.search(any(SearchRequest.class), eq(EsFeatureCollectionModel.class))).thenReturn(mockResponse);

        ElasticSearchBase.SearchResult<FeatureGeoJSON> result = elasticSearch.searchFeatureSummary(collectionId, properties, filter);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getCollections().size());
        assertEquals(1L, result.getTotal());
        // validate geometry keeps same after adding key property
        assertEquals(esFeature.toFeatureGeoJSON().getGeometry(),
                result.getCollections().get(0).getGeometry());

        // validate key is in properties
        FeatureGeoJSON returnedFeature = result.getCollections().get(0);
        JsonNullable<Object> props = returnedFeature.getProperties();

        assertInstanceOf(Map.class, props.get());
        Map<?, ?> featureProps = (Map<?, ?>)props.get();

        assertTrue(featureProps.containsKey("key"));
        assertEquals("satellite_ghrsst_l4_gamssa_1day_multi_sensor_world.zarr",
                featureProps.get("key"));

        // validate the feature properties are correctly serialised
        assertEquals("1939-09", featureProps.get("date"));
        assertEquals(5, featureProps.get("count"));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JsonNullableModule());

        String json = mapper.writeValueAsString(returnedFeature);

        assertTrue(json.contains("\"date\":\"1939-09\""));
        assertTrue(json.contains("\"count\":5"));
        assertFalse(json.contains("\"present\":true"));
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

        assertEquals(9, capturingSearch.should.size(),
                "Exact match should produce 9 queries (title + description + other fields)");
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

        assertEquals(9, capturingSearch.should.size(), "Fuzzy match should produce 9 queries");
        assertTrue(capturingSearch.should.get(0).isMatch(), "fuzzy_title should be MatchQuery");
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
        assertTrue(capturingSearch.explainRequest.query().isScriptScore());
        assertEquals(9, capturingSearch.explainRequest.query().scriptScore()
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
        assertTrue(request.query().isBool());
        assertEquals("type", request.query().bool().must().get(0).match().field());
        assertEquals("Collection", request.query().bool().must().get(0).match().query().stringValue());
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
