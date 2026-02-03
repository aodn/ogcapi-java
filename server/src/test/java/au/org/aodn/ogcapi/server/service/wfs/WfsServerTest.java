package au.org.aodn.ogcapi.server.service.wfs;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static au.org.aodn.ogcapi.server.core.service.wfs.WfsDefaultParam.WFS_LINK_MARKER;
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

    @Autowired
    @Qualifier("pretendUserEntity")
    private HttpEntity<?> entity;

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
    void noCollection_returnsEmptyFeatureTypes() {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(Collections.emptyList());
        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate), entity);

        List<FeatureTypeInfo> featureTypes = Collections.singletonList(FeatureTypeInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterFeatureTypesByWfsLinks("id", featureTypes));
    }

    @Test
    void noWfsLinks_returnsEmptyFeatureTypes() {
        StacCollectionModel model = mock(StacCollectionModel.class);
        when(model.getLinks()).thenReturn(Collections.emptyList());

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));

        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate), entity);

        List<FeatureTypeInfo> featureTypes = Collections.singletonList(FeatureTypeInfo.builder().build());
        assertEquals(Collections.emptyList(), server.filterFeatureTypesByWfsLinks("id", featureTypes));
    }

    /**
     * The function should find one because title name matches
     */
    @Test
    void primaryTitleMatch_filtersMatchingFeatureTypes() {
        LinkModel wfsLink = LinkModel.builder()
                .title("test_feature_type")
                .aiGroup(WFS_LINK_MARKER)
                .href("http://example.com?wfs").build();

        StacCollectionModel model = StacCollectionModel.builder().links(List.of(wfsLink)).build();
        var featureTypes = List.of(
                FeatureTypeInfo.builder().title("test_feature_type").name("").build(),
                FeatureTypeInfo.builder().title("other").build()
        );

        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(model));
        when(mockSearch.searchCollections(anyString())).thenReturn(result);

        WfsServer server = new WfsServer(mockSearch, downloadableFieldsService, restTemplate, new RestTemplateUtils(restTemplate), entity);

        List<FeatureTypeInfo> info = server.filterFeatureTypesByWfsLinks("id", featureTypes);
        assertEquals(1, info.size(), "FeatureType count match");
        assertEquals(featureTypes.get(0), info.get(0), "FeatureType test_feature_type found");
    }
}
