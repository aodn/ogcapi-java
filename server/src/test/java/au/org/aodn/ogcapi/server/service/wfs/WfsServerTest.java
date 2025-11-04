package au.org.aodn.ogcapi.server.service.wfs;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WfsServerTest {
    /**
     * Test null case where the dataset have the collection id not found
     */
    @Test
    void noCollection_returnsEmptyLayers() {
        Search mockSearch = mock(Search.class);
        DownloadableFieldsService downloadableFieldsService = mock(DownloadableFieldsService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(Collections.emptyList());
        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate));

        List<LayerInfo> layers = Collections.singletonList(LayerInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterLayersByWfsLinks("id", layers));
    }

    @Test
    void noWfsLinks_returnsEmptyLayers() {
        Search mockSearch = mock(Search.class);
        DownloadableFieldsService downloadableFieldsService = mock(DownloadableFieldsService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        StacCollectionModel model = mock(StacCollectionModel.class);
        when(model.getLinks()).thenReturn(Collections.emptyList());

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));

        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate));

        List<LayerInfo> layers = Collections.singletonList(LayerInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterLayersByWfsLinks("id", layers));
    }
}
