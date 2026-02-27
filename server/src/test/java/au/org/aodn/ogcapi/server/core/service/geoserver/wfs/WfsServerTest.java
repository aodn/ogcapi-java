package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.exception.GeoserverFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public class WfsServerTest {

    @Mock
    Search mockSearch;

    @Mock
    RestTemplate restTemplate;

    @Mock
    HttpEntity<?> entity;

    @Autowired
    WfsDefaultParam wfsDefaultParam;

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

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

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

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

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

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

        List<FeatureTypeInfo> info = server.filterFeatureTypesByWfsLinks("id", featureTypes);
        assertEquals(1, info.size(), "FeatureType count match");
        assertEquals(featureTypes.get(0), info.get(0), "FeatureType test_feature_type found");
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
                    <xsd:element name="testLayer" type="test:testLayerType"/>
                </xsd:schema>
                """;

        WfsServer.WfsFeatureRequest request = WfsServer.WfsFeatureRequest.builder().layerName("test:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(List.of(
                StacCollectionModel.builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        ));

        String id = "id";

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockWfsResponse, HttpStatus.OK));

        when(mockSearch.searchCollections(eq(id)))
                .thenReturn(stac);

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);
        WfsFields result = server.getDownloadableFields(id, request);

        assertNotNull(result);
        assertNotNull(result.getFields());
        assertEquals("testLayer", result.getTypename());
        assertEquals(3, result.getFields().size());

        // Check geometry field
        WfsField geomField = result.getFields().stream()
                .filter(f -> "geom".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(geomField);
        assertEquals("geom", geomField.getLabel());
        assertEquals("GeometryPropertyType", geomField.getType());

        // Check datetime field
        WfsField timeField = result.getFields().stream()
                .filter(f -> "timestamp".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(timeField);
        assertEquals("timestamp", timeField.getLabel());
        assertEquals("dateTime", timeField.getType());

        // Check string field
        WfsField nameField = result.getFields().stream()
                .filter(f -> "name".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(nameField);
        assertEquals("name", nameField.getLabel());
        assertEquals("string", nameField.getType());
    }

    @Test
    public void testGetDownloadableFieldsNotFoundResponse() {
        // Mock WFS response with NOT_FOUND status
        WfsServer.WfsFeatureRequest request = WfsServer.WfsFeatureRequest.builder().layerName("test:layer2").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(List.of(
                StacCollectionModel.builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        ));

        String id = "id2";

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

        when(mockSearch.searchCollections(eq(id)))
                .thenReturn(stac);

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

        GeoserverFieldsNotFoundException exception = assertThrows(
                GeoserverFieldsNotFoundException.class,
                () -> server.getDownloadableFields(id, request)
        );

        assertEquals("No downloadable fields found for all url",
                exception.getMessage(),
                "Exception not match"
        );
    }

    @Test
    public void testGetDownloadableFieldsWfsError() {
        WfsServer.WfsFeatureRequest request = WfsServer.WfsFeatureRequest.builder().layerName("invalid:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(List.of(
                StacCollectionModel.builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        ));

        String id = "id3";

        when(mockSearch.searchCollections(eq(id)))
                .thenReturn(stac);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

        GeoserverFieldsNotFoundException exception = assertThrows(
                GeoserverFieldsNotFoundException.class,
                () -> server.getDownloadableFields(id, request)
        );

        assertTrue(exception.getMessage().contains("No downloadable fields found"));
    }

    @Test
    public void testGetDownloadableFieldsNetworkError() {
        WfsServer.WfsFeatureRequest request = WfsServer.WfsFeatureRequest.builder().layerName("test:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(List.of(
                StacCollectionModel.builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/ows")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        ));

        String id = "id4";

        when(mockSearch.searchCollections(eq(id)))
                .thenReturn(stac);

        // Mock network error
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> server.getDownloadableFields(id, request)
        );

        assertTrue(exception.getMessage().contains("Connection timeout"));
    }

    @Test
    public void testGetDownloadableFieldsNoCollection() {
        WfsServer.WfsFeatureRequest request = WfsServer.WfsFeatureRequest.builder().layerName("test:layer").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(Collections.emptyList());
        String id = "id5";

        when(mockSearch.searchCollections(eq(id)))
                .thenReturn(stac);

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);

        WfsFields result = server.getDownloadableFields(id, request);

        assertNull(result, "Should return null when no collection found");
    }

    @Test
    void createFeatureFieldQueryUrl_buildsCorrectUrlWithTypename() {
        // arrange
        String baseUrl = "https://example.com/wfs?service=WFS&version=1.1.0&request=GetFeature";
        FeatureRequest request = FeatureRequest
                .builder()
                .layerName("my:layer")
                .build();

        // act
        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);
        String result = server.createFeatureFieldQueryUrl(baseUrl, request);

        // assert
        assertNotNull(result);
        assertTrue(result.contains("TYPENAME=my:layer"));
        assertTrue(result.contains("SERVICE=WFS"));
        assertTrue(result.contains("VERSION=2.0.0"));      // from defaults
        assertTrue(result.contains("REQUEST=DescribeFeatureType")); // original one is replaced
    }

    @Test
    void createWfsRequestUrl_stripsOldParamsFromServerUrl() {
        // The server URL already has query params that would cause duplicates
        String serverUrlWithParams = "https://geoserver.imas.utas.edu.au/geoserver/seamap/wfs"
                + "?version=1.0.0&request=GetFeature&typeName=SeamapAus_VIC_statewide_habitats_2023&outputFormat=SHAPE-ZIP";

        String layerName = "seamap:SeamapAus_VIC_statewide_habitats_2023";
        String outputFormat = "text/csv";

        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);
        String result = server.createWfsRequestUrl(serverUrlWithParams, layerName, null, null, outputFormat, 1L, false);

        assertNotNull(result);

        // Old param values from the server URL must NOT appear
        assertFalse(result.contains("SHAPE-ZIP"), "Old outputFormat value should be removed");
        assertFalse(result.contains("typeName=SeamapAus_VIC_statewide_habitats_2023&"),
                "Old typeName (without namespace prefix) should be removed");

        // New param values must be present
        assertTrue(result.contains("typeName=seamap:SeamapAus_VIC_statewide_habitats_2023"),
                "New typeName with namespace prefix should be present");
        assertTrue(result.contains("outputFormat=text/csv"),
                "New outputFormat should be present");

        // Default download params from config
        assertTrue(result.contains("SERVICE=WFS"), "SERVICE param should be present");
        assertTrue(result.contains("VERSION=1.1.0"), "VERSION param should be present");
        assertTrue(result.contains("REQUEST=GetFeature"), "REQUEST param should be present");

        // No duplicate keys — each param name should appear exactly once
        String query = result.substring(result.indexOf('?') + 1);
        String[] pairs = query.split("&");
        long typeNameCount = java.util.Arrays.stream(pairs).filter(p -> p.toLowerCase().startsWith("typename=")).count();
        long outputFormatCount = java.util.Arrays.stream(pairs).filter(p -> p.toLowerCase().startsWith("outputformat=")).count();
        long versionCount = java.util.Arrays.stream(pairs).filter(p -> p.toLowerCase().startsWith("version=")).count();
        long requestCount = java.util.Arrays.stream(pairs).filter(p -> p.toLowerCase().startsWith("request=")).count();

        assertEquals(1, typeNameCount, "typeName should appear exactly once");
        assertEquals(1, outputFormatCount, "outputFormat should appear exactly once");
        assertEquals(1, versionCount, "VERSION should appear exactly once");
        assertEquals(1, requestCount, "REQUEST should appear exactly once");
    }

    @Test
    void createCapabilitiesQueryUrl_buildsCorrectUrl() {
        // arrange
        String baseUrl = "https://example.com/wfs?service=WFS&version=1.1.0&request=GetFeature";

        // act
        WfsServer server = new WfsServer(mockSearch, restTemplate, new RestTemplateUtils(restTemplate), entity, wfsDefaultParam);
        String result = server.createCapabilitiesQueryUrl(baseUrl);

        // assert
        assertNotNull(result);
        assertTrue(result.contains("SERVICE=WFS"));
        assertTrue(result.contains("VERSION=2.0.0"));      // from defaults
        assertTrue(result.contains("REQUEST=GetCapabilities")); // original one is replaced
    }
}
