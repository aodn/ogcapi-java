package au.org.aodn.ogcapi.server.service.wfs;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WfsServerTest {

    @Mock
    Search mockSearch;

    @Mock
    DownloadableFieldsService downloadableFieldsService;

    @Mock
    RestTemplate restTemplate;

    AutoCloseable closeableMock;

    @BeforeEach
    public void setUp() {
        closeableMock = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanUp() throws Exception {
        closeableMock.close();
    }
    /**
     * Test null case where the dataset have the collection id not found
     */
    @Test
    void noCollection_returnsEmptyLayers() {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(Collections.emptyList());
        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate));

        List<LayerInfo> layers = Collections.singletonList(LayerInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterLayersByWfsLinks("id", layers));
    }

    @Test
    void noWfsLinks_returnsEmptyLayers() {
        StacCollectionModel model = mock(StacCollectionModel.class);
        when(model.getLinks()).thenReturn(Collections.emptyList());

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));

        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate));

        List<LayerInfo> layers = Collections.singletonList(LayerInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterLayersByWfsLinks("id", layers));
    }
    /**
     * The function should fine one because title name matches
     */
    @Test
    void primaryTitleMatch_filtersMatchingLayers() {
        LinkModel wfsLink = LinkModel.builder()
                .title("test_layer")
                .aiGroup("Data Access > wfs")
                .href("http://example.com?wfs").build();

        StacCollectionModel model = StacCollectionModel.builder().links(List.of(wfsLink)).build();
        var layers = List.of(
                LayerInfo.builder().title("test_layer").name("").build(),
                LayerInfo.builder().title("other").build()
        );

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));
        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate));

        List<LayerInfo> info = server.filterLayersByWfsLinks("id", layers);
        assertEquals(1, info.size(), "Layer match");
        assertEquals(layers.get(0), info.get(0), "Layer test_layer found");
    }
}
