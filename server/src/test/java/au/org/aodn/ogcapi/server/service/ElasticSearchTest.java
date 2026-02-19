package au.org.aodn.ogcapi.server.service;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.server.core.model.EsFeatureCollectionModel;
import au.org.aodn.ogcapi.server.core.model.EsFeatureModel;
import au.org.aodn.ogcapi.server.core.model.EsPolygonModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        List<FeatureRequest.PropertyName> properties = List.of(FeatureRequest.PropertyName.wildcard);
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
        @SuppressWarnings("unchecked")
        Map<String, Object> featureProps = (Map<String, Object>) returnedFeature.getProperties();

        assertTrue(featureProps.containsKey("key"));
        assertEquals("satellite_ghrsst_l4_gamssa_1day_multi_sensor_world.zarr",
                featureProps.get("key"));
    }


}
