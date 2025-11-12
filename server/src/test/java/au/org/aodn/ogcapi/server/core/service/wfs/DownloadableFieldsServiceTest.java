package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.mapper.StacToCollection;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureId;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.DasService;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import au.org.aodn.ogcapi.server.features.RestApi;
import au.org.aodn.ogcapi.server.features.RestServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static au.org.aodn.ogcapi.server.core.service.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        TestConfig.class,
        Config.class,
        RestApi.class,
        RestServices.class,
        WfsServer.class,
        WfsDefaultParam.class,
        DownloadableFieldsService.class,
        JacksonAutoConfiguration.class,
        CacheAutoConfiguration.class
})
public class DownloadableFieldsServiceTest {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WfsServer wfsServerConfig;

    @Autowired
    private DownloadableFieldsService downloadableFieldsService;

    @Autowired
    private RestApi restApi;

    @Autowired
    private WfsServer wfsServer;

    @Autowired
    @Qualifier("pretendUserEntity")
    private HttpEntity<?> entity;

    @MockitoBean
    private DasService dasService;

    @MockitoBean
    private StacToCollection stacToCollection;

    @MockitoBean
    private WmsServer wmsServer;

    @MockitoBean
    private ElasticSearch search;

    private static final String AUTHORIZED_SERVER = "https://geoserver-123.aodn.org.au/geoserver/wfs";
    private static final String UNAUTHORIZED_SERVER = "https://unauthorized-server.com/wfs";

    // Helper method to create FeatureRequest for testing
    private FeatureRequest createDownloadableFieldsRequest(String serverUrl, String layerName) {
        return FeatureRequest.builder()
                .layerName(layerName)
                .build();
    }

    @BeforeEach
    public void resetMock() {
        Mockito.reset(search);
    }

    @Test
    public void testGetDownloadableFieldsSuccess() {
        // Mock successful WFS response with geometry and datetime fields
        String mockWfsResponse = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                            xmlns:gml="http://www.opengis.net/gml/3.2"
                            xmlns:test="test.namespace"
                            targetNamespace="test.namespace">
                    <xsd:complexType name="testLayerType">
                        <xsd:complexContent>
                            <xsd:extension base="gml:AbstractFeatureType">
                                <xsd:sequence>
                                    <xsd:element name="geom" type="gml:GeometryPropertyType"/>
                                    <xsd:element name="timestamp" type="xsd:dateTime"/>
                                    <xsd:element name="name" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:extension>
                        </xsd:complexContent>
                    </xsd:complexType>
                </xsd:schema>
                """;

        FeatureRequest request = FeatureRequest.builder().layerName("test:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );

        String id = "id";

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockWfsResponse, HttpStatus.OK));

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);

        List<DownloadableFieldModel> result = wfsServer.getDownloadableFields(id, request, null);

        assertNotNull(result);
        assertEquals(2, result.size());

        // Check geometry field
        DownloadableFieldModel geomField = result.stream()
                .filter(f -> "geom".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(geomField);
        assertEquals("geom", geomField.getLabel());
        assertEquals("geometrypropertytype", geomField.getType());

        // Check datetime field
        DownloadableFieldModel timeField = result.stream()
                .filter(f -> "timestamp".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(timeField);
        assertEquals("timestamp", timeField.getLabel());
        assertEquals("dateTime", timeField.getType());
    }

    @Test
    public void testGetDownloadableFieldsEmptyResponse() {
        // Mock WFS response with no geometry or datetime fields
        String mockWfsResponse = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                            xmlns:gml="http://www.opengis.net/gml/3.2"
                            xmlns:test="test.namespace"
                            targetNamespace="test.namespace">
                    <xsd:complexType name="testLayerType">
                        <xsd:complexContent>
                            <xsd:extension base="gml:AbstractFeatureType">
                                <xsd:sequence>
                                    <xsd:element name="name" type="xsd:string"/>
                                    <xsd:element name="id" type="xsd:int"/>
                                </xsd:sequence>
                            </xsd:extension>
                        </xsd:complexContent>
                    </xsd:complexType>
                </xsd:schema>
                """;
        FeatureRequest request = FeatureRequest.builder().layerName("test:layer2").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );

        String id = "id2";

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockWfsResponse, HttpStatus.NOT_FOUND));

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);

        DownloadableFieldsNotFoundException exception = assertThrows(
                DownloadableFieldsNotFoundException.class,
                () -> wfsServer.getDownloadableFields(id, request, null)
        );

        assertEquals("No downloadable fields found for all url",
                exception.getMessage(),
                "Exception not match"
        );
    }

    @Test
    public void testGetDownloadableFieldsWfsError() {
        FeatureRequest request = FeatureRequest.builder().layerName("invalid:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );

        String id = "id3";

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        DownloadableFieldsNotFoundException exception = assertThrows(
                DownloadableFieldsNotFoundException.class,
                () -> wfsServer.getDownloadableFields(id, request, null)
        );

        assertTrue(exception.getMessage().contains("No downloadable fields found"));
    }

    @Test
    public void testGetDownloadableFieldsNetworkError() {
        FeatureRequest request = FeatureRequest.builder().layerName("test:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );

        String id = "id4";

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);

        // Mock network error
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> wfsServer.getDownloadableFields(id, request, null)
        );

        assertTrue(exception.getMessage().contains("Connection timeout"));
    }

    @Test
    public void testRestApiDownloadableFieldsMissingWongUuid() {
        FeatureRequest request = createDownloadableFieldsRequest(null, "test:layer");

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        String id = "id5";

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);

        ResponseEntity<?> response = restApi.getFeature(
                id,
                FeatureId.wfs_downloadable_fields.name(),
                request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), "Should return 400 for missing wong uuid");
    }

    @Test
    public void testRestApiDownloadableFieldsMissingLayerName() {
        FeatureRequest request = createDownloadableFieldsRequest("https://test.com/wfs", null);

        ResponseEntity<?> response = restApi.getFeature(
                "test-collection",
                FeatureId.wfs_downloadable_fields.name(),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "Should return 400 for missing layerName");
    }
}
