package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.configuration.WfsWmsConfig;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.DescribeLayerResponse;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.wfs.DownloadableFieldsService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsDefaultParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static au.org.aodn.ogcapi.server.core.service.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static au.org.aodn.ogcapi.server.core.service.wms.WmsDefaultParam.WMS_LINK_MARKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                TestConfig.class,
                Config.class,
                WfsWmsConfig.class,
                WmsDefaultParam.class,
                WfsDefaultParam.class,
                DownloadableFieldsService.class,
                JacksonAutoConfiguration.class,
                CacheAutoConfiguration.class}
)
public class WmsServerTest {
    // Inject the mock bean on the fly to avoid dependency issue
    // we do not need this bean's function in this test
    @MockitoBean
    protected Search search;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected WmsServer wmsServer;

    @BeforeEach
    public void before() {
        Mockito.reset(restTemplate);
    }

    @Test
    public void verifyWfsDescribeLayerUrlGenCorrect() {
        FeatureRequest featureRequest = FeatureRequest
                .builder()
                .layerName("imos:argo_profile_map")
                .build();

        List<String> urls = wmsServer.createMapDescribeUrl("http://geoserver-123.aodn.org.au/geoserver/wms", "id", featureRequest);

        assertEquals(2, urls.size());

        // First url will be cache one
        String expectedUrl = "https://tilecache.aodn.org.au/geowebcache/service/wms?LAYERS=imos:argo_profile_map&SERVICE=WMS&VERSION=1.1.1&REQUEST=DescribeLayer";
        UriComponents expected = UriComponentsBuilder.fromUriString(expectedUrl).build();
        UriComponents cached = UriComponentsBuilder.fromUriString(urls.get(0)).build();

        assertEquals(expected.getScheme(), cached.getScheme());
        assertEquals(expected.getPath(), cached.getPath());
        assertEquals(expected.getHost(), cached.getHost());

        expected.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected.getQueryParams().get(key), cached.getQueryParams().get(key), key)
                );

        // The second url will be the original without cache
        expectedUrl = "http://geoserver-123.aodn.org.au/geoserver/wms?LAYERS=imos:argo_profile_map&SERVICE=WMS&VERSION=1.1.1&REQUEST=DescribeLayer";
        UriComponents expected2 = UriComponentsBuilder.fromUriString(expectedUrl).build();

        UriComponents nonCache = UriComponentsBuilder.fromUriString(urls.get(1)).build();
        assertEquals(expected2.getScheme(), nonCache.getScheme());
        assertEquals(expected2.getPath(), nonCache.getPath());
        assertEquals(expected2.getHost(), nonCache.getHost());

        expected2.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected2.getQueryParams().get(key), nonCache.getQueryParams().get(key), key)
                );
    }

    @Test
    public void verifyWfsDescribeLayerUrlGenCorrectForNCWMS() {
        FeatureRequest featureRequest = FeatureRequest
                .builder()
                .layerName("srs_ghrsst_l4_gamssa_url/analysed_sst")
                .build();

        List<String> urls = wmsServer.createMapDescribeUrl("http://geoserver-123.aodn.org.au/geoserver/ncwms", "id", featureRequest);

        assertEquals(2, urls.size());

        // First url will be cache one
        String expectedUrl = "http://geoserver-123.aodn.org.au/geoserver/ncwms?LAYERS=srs_ghrsst_l4_gamssa_url/analysed_sst&SERVICE=WMS&VERSION=1.1.1&REQUEST=DescribeLayer";
        UriComponents expected = UriComponentsBuilder.fromUriString(expectedUrl).build();
        UriComponents cached1 = UriComponentsBuilder.fromUriString(urls.get(0)).build();

        assertEquals(expected.getScheme(), cached1.getScheme());
        assertEquals(expected.getPath(), cached1.getPath());
        assertEquals(expected.getHost(), cached1.getHost());

        expected.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected.getQueryParams().get(key), cached1.getQueryParams().get(key), key)
                );

        // The second url will be the original without cache
        expectedUrl = "http://geoserver-123.aodn.org.au/geoserver/ncwms?LAYERS=srs_ghrsst_l4_gamssa_url&SERVICE=WMS&VERSION=1.1.1&REQUEST=DescribeLayer";
        UriComponents expected2 = UriComponentsBuilder.fromUriString(expectedUrl).build();

        UriComponents nonCache = UriComponentsBuilder.fromUriString(urls.get(1)).build();
        assertEquals(expected2.getScheme(), nonCache.getScheme());
        assertEquals(expected2.getPath(), nonCache.getPath());
        assertEquals(expected2.getHost(), nonCache.getHost());

        expected2.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected2.getQueryParams().get(key), nonCache.getQueryParams().get(key), key)
                );
    }

    @Test
    public void verifyWfsUrlGenCorrect() {
        FeatureRequest featureRequest = FeatureRequest
                .builder()
                .layerName("imos:argo_profile_map")
                .width(BigDecimal.valueOf(637L))
                .height(BigDecimal.valueOf(550L))
                .x(BigDecimal.valueOf(474L))
                .y(BigDecimal.valueOf(252L))
                .bbox(List.of(
                        BigDecimal.valueOf(-111.86719179153421),
                        BigDecimal.valueOf(-69.03714171275249),
                        BigDecimal.valueOf(111.8671917915342),
                        BigDecimal.valueOf(69.03714171275138)))
                .build();

        List<String> urls = wmsServer.createMapFeatureQueryUrl("http://geoserver-123.aodn.org.au/geoserver/wms", "id", featureRequest);

        assertEquals(2, urls.size());

        // First url will be cache one
        String expectedUrl = "https://tilecache.aodn.org.au/geowebcache/service/wms?LAYERS=imos:argo_profile_map&TRANSPARENT=TRUE&VERSION=1.1.1&FORMAT=text/html&EXCEPTIONS=application/vnd.ogc.se_xml&TILED=true&SERVICE=WMS&REQUEST=GetFeatureInfo&STYLES=&QUERYABLE=true&SRS=EPSG:4326&INFO_FORMAT=text/html&FEATURE_COUNT=100&BUFFER=10&WIDTH=637&HEIGHT=550&I=474&J=252&X=474&Y=252&QUERY_LAYERS=imos:argo_profile_map&BBOX=-111.86719179153421,-69.03714171275249,111.8671917915342,69.03714171275138";
        UriComponents expected = UriComponentsBuilder.fromUriString(expectedUrl).build();
        UriComponents cached = UriComponentsBuilder.fromUriString(urls.get(0)).build();

        assertEquals(expected.getScheme(), cached.getScheme());
        assertEquals(expected.getPath(), cached.getPath());
        assertEquals(expected.getHost(), cached.getHost());

        expected.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected.getQueryParams().get(key), cached.getQueryParams().get(key), key)
                );

        // The second url will be the original without cache
        expectedUrl = "http://geoserver-123.aodn.org.au/geoserver/wms?LAYERS=imos:argo_profile_map&TRANSPARENT=TRUE&VERSION=1.1.1&FORMAT=text/html&EXCEPTIONS=application/vnd.ogc.se_xml&TILED=true&SERVICE=WMS&REQUEST=GetFeatureInfo&STYLES=&QUERYABLE=true&SRS=EPSG:4326&INFO_FORMAT=text/html&FEATURE_COUNT=100&BUFFER=10&WIDTH=637&HEIGHT=550&I=474&J=252&X=474&Y=252&QUERY_LAYERS=imos:argo_profile_map&BBOX=-111.86719179153421,-69.03714171275249,111.8671917915342,69.03714171275138";
        UriComponents expected2 = UriComponentsBuilder.fromUriString(expectedUrl).build();

        UriComponents nonCache = UriComponentsBuilder.fromUriString(urls.get(1)).build();
        assertEquals(expected2.getScheme(), nonCache.getScheme());
        assertEquals(expected2.getPath(), nonCache.getPath());
        assertEquals(expected2.getHost(), nonCache.getHost());

        expected2.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected2.getQueryParams().get(key), nonCache.getQueryParams().get(key), key)
                );
    }

    @Test
    public void verifyNcwmsUrlGenCorrect() {
        FeatureRequest featureRequest = FeatureRequest
                .builder()
                .layerName("srs_ghrsst_l4_gamssa_url/analysed_sst")
                .width(BigDecimal.valueOf(637L))
                .height(BigDecimal.valueOf(550L))
                .x(BigDecimal.valueOf(254L))
                .y(BigDecimal.valueOf(191L))
                .bbox(List.of(
                        BigDecimal.valueOf(-111.86719179153421),
                        BigDecimal.valueOf(-69.03714171275249),
                        BigDecimal.valueOf(111.8671917915342),
                        BigDecimal.valueOf(69.03714171275138)))
                .build();

        List<String> urls = wmsServer.createMapFeatureQueryUrl("https://geoserver-123.aodn.org.au/geoserver/ncwms", "id", featureRequest);

        assertEquals(1, urls.size());

        String expectedUrl = "https://geoserver-123.aodn.org.au/geoserver/ncwms?LAYERS=srs_ghrsst_l4_gamssa_url/analysed_sst&TRANSPARENT=TRUE&VERSION=1.3.0&FORMAT=text/xml&EXCEPTIONS=application/vnd.ogc.se_xml&TILED=true&SERVICE=ncwms&REQUEST=GetFeatureInfo&STYLES=&QUERYABLE=true&CRS=EPSG:4326&INFO_FORMAT=text/xml&FEATURE_COUNT=100&BUFFER=10&WIDTH=637&HEIGHT=550&I=254&J=191&X=254&Y=191&QUERY_LAYERS=srs_ghrsst_l4_gamssa_url/analysed_sst&BBOX=-111.86719179153421,-69.03714171275249,111.8671917915342,69.03714171275138";
        UriComponents expected = UriComponentsBuilder.fromUriString(expectedUrl).build();
        UriComponents result = UriComponentsBuilder.fromUriString(urls.get(0)).build();

        assertEquals(expected.getScheme(), result.getScheme());
        assertEquals(expected.getPath(), result.getPath());
        assertEquals(expected.getHost(), result.getHost());

        expected.getQueryParams()
                .forEach((key, value) ->
                        assertEquals(expected.getQueryParams().get(key), result.getQueryParams().get(key))
                );
    }

    @Test
    public void verifyHandleServiceExceptionReportCorrect() {
        FeatureRequest request = FeatureRequest.builder().layerName("srs_ghrsst_l4_gamssa_url/").build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/wms")
                                        .title(request.getLayerName())
                                        .aiGroup(WMS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );

        String r = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<!DOCTYPE ServiceExceptionReport SYSTEM \"https://geoserver-123.aodn.org.au/geoserver/schemas/wms/1.1.1/WMS_exception_1_1_1.dtd\">\n" +
                "<ServiceExceptionReport version=\"1.1.1\" >\n" +
                "    <ServiceException code=\"LayerNotDefined\" locator=\"MapLayerInfoKvpParser\">\n" +
                "      srs_ghrsst_l4_gamssa_url/: no such layer on this server\n" +
                "</ServiceException>\n" +
                "</ServiceExceptionReport>";

        when(restTemplate.getForEntity(anyString(), eq(String.class), eq(Collections.emptyMap())))
                .thenReturn(ResponseEntity.ok(r));

        String id = "id";

        when(search.searchCollections(eq(id)))
                .thenReturn(stac);
        DescribeLayerResponse response = wmsServer.describeLayer(id, request);

        assertNull(response, "Null expected as Service Exception ignored");
    }

    @Test
    public void verifyParseCorrect() throws JsonProcessingException {
        DescribeLayerResponse value = wmsServer.xmlMapper.readValue(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE WMS_DescribeLayerResponse SYSTEM \"https://geoserver-123.aodn.org.au/geoserver/schemas/wms/1.1.1/WMS_DescribeLayerResponse.dtd\">\n" +
                "<WMS_DescribeLayerResponse version=\"1.1.1\">\n" +
                "    <LayerDescription name=\"imos:srs_ghrsst_l4_gamssa_url\" wfs=\"https://geoserver-123.aodn.org.au/geoserver/wfs?\" owsURL=\"https://geoserver-123.aodn.org.au/geoserver/wfs?\" owsType=\"WFS\">\n" +
                "        <Query typeName=\"imos:srs_ghrsst_l4_gamssa_url\"/>\n" +
                "    </LayerDescription>\n" +
                "</WMS_DescribeLayerResponse>", DescribeLayerResponse.class);

        assertEquals("imos:srs_ghrsst_l4_gamssa_url", value.getLayerDescription().getName());
        assertEquals("https://geoserver-123.aodn.org.au/geoserver/wfs?", value.getLayerDescription().getWfs());
        assertEquals("imos:srs_ghrsst_l4_gamssa_url", value.getLayerDescription().getQuery().getTypeName());
    }
    /**
     * Test with only one dateTime field in the describe layer
     * @throws JsonProcessingException - Not expected
     */
    @Test
    public void verifyCreateCQLSingleDateTime() throws JsonProcessingException {
        String uuid = "uuid1";
        String layer = "imos:srs_ghrsst_l4_gamssa_url";
        FeatureRequest request = FeatureRequest.builder()
                .datetime("2023-01-01/2023-12-31")
                .layerName(layer)
                .build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/wfs")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );
        // This sample contains 1 dateTime field
        String value =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xsd:schema xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:imos=\"imos.mod\" xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"imos.mod\">\n" +
                        "  <xsd:import namespace=\"http://www.opengis.net/gml/3.2\" schemaLocation=\"https://geoserver-123.aodn.org.au/geoserver/schemas/gml/3.2.1/gml.xsd\"/>\n" +
                        "  <xsd:complexType name=\"srs_ghrsst_l4_gamssa_urlType\">\n" +
                        "    <xsd:complexContent>\n" +
                        "      <xsd:extension base=\"gml:AbstractFeatureType\">\n" +
                        "        <xsd:sequence>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"timestep_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"collection_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"file_url\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"size\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"time\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "        </xsd:sequence>\n" +
                        "      </xsd:extension>\n" +
                        "    </xsd:complexContent>\n" +
                        "  </xsd:complexType>\n" +
                        "  <xsd:element name=\"srs_ghrsst_l4_gamssa_url\" substitutionGroup=\"gml:AbstractFeature\" type=\"imos:srs_ghrsst_l4_gamssa_urlType\"/>\n" +
                        "</xsd:schema>";

        when(search.searchCollections(eq(uuid)))
                .thenReturn(stac);

        when(restTemplate.getForEntity(
                anyString(),
                eq(String.class))
        ).thenReturn(ResponseEntity.ok(value));

        String result = wmsServer.createCQLFilter(uuid, request);

        assertEquals("CQL_FILTER=time DURING 2023-01-01/2023-12-31", result);
    }
    /**
     * Test with only one dateTime field in the describe layer with predefined cql
     * @throws JsonProcessingException - Not expected
     */
    @Test
    public void verifyCreateCQLSingleDateTimeWithCQL() throws JsonProcessingException {
        String uuid = "uuid1";
        String layer = "imos:srs_ghrsst_l4_gamssa_url";
        FeatureRequest request = FeatureRequest.builder()
                .datetime("2023-01-01/2023-12-31")
                .layerName(layer)
                .build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/wfs?cql_filter=set_code=1234")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );
        // This sample contains 1 dateTime field
        String value =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xsd:schema xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:imos=\"imos.mod\" xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"imos.mod\">\n" +
                        "  <xsd:import namespace=\"http://www.opengis.net/gml/3.2\" schemaLocation=\"https://geoserver-123.aodn.org.au/geoserver/schemas/gml/3.2.1/gml.xsd\"/>\n" +
                        "  <xsd:complexType name=\"srs_ghrsst_l4_gamssa_urlType\">\n" +
                        "    <xsd:complexContent>\n" +
                        "      <xsd:extension base=\"gml:AbstractFeatureType\">\n" +
                        "        <xsd:sequence>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"timestep_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"collection_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"file_url\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"size\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"time\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "        </xsd:sequence>\n" +
                        "      </xsd:extension>\n" +
                        "    </xsd:complexContent>\n" +
                        "  </xsd:complexType>\n" +
                        "  <xsd:element name=\"srs_ghrsst_l4_gamssa_url\" substitutionGroup=\"gml:AbstractFeature\" type=\"imos:srs_ghrsst_l4_gamssa_urlType\"/>\n" +
                        "</xsd:schema>";

        when(search.searchCollections(eq(uuid)))
                .thenReturn(stac);

        when(restTemplate.getForEntity(
                anyString(),
                eq(String.class))
        ).thenReturn(ResponseEntity.ok(value));

        String result = wmsServer.createCQLFilter(uuid, request);

        assertEquals("CQL_FILTER=time DURING 2023-01-01/2023-12-31 AND set_code=1234", result);
    }
    /**
     * Test with only two or more dateTime field in the describe layer with predefined cql
     * @throws JsonProcessingException - Not expected
     */
    @Test
    public void verifyCreateCQLMultiDateTimeWithCQL() throws JsonProcessingException {
        String uuid = "uuid1";
        String layer = "aatams_sattag_qc_ctd_profile_map";
        FeatureRequest request = FeatureRequest.builder()
                .datetime("2023-01-01/2023-12-31")
                .layerName(layer)
                .build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/wfs?cql_filter=set_code=1234")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );
        // This sample contains 1 dateTime field
        String value =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xsd:schema xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:imos=\"imos.mod\" xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"imos.mod\">\n" +
                        "  <xsd:import namespace=\"http://www.opengis.net/gml/3.2\" schemaLocation=\"https://geoserver-portal.aodn.org.au/geoserver/schemas/gml/3.2.1/gml.xsd\"/>\n" +
                        "  <xsd:complexType name=\"aatams_sattag_qc_ctd_profile_mapType\">\n" +
                        "    <xsd:complexContent>\n" +
                        "      <xsd:extension base=\"gml:AbstractFeatureType\">\n" +
                        "        <xsd:sequence>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"file_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_no\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"platform_number\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"project_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"pi_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"cycle_number\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"direction\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_centre\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"dc_reference\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_state_indicator\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_mode\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"inst_reference\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"wmo_inst_type\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"juld\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"juld_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"juld_location\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"latitude\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"longitude\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"position_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"positioning_system\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_pres_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_temp_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_psal_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"position\" nillable=\"true\" type=\"gml:GeometryPropertyType\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"smru_platform_code\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"wmo_platform_code\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"species_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"release_location\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"id\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"job_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"url\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"created\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"modified\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"first_indexed\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"last_indexed\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"last_indexed_run\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"size\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"deleted\" nillable=\"true\" type=\"xsd:boolean\"/>\n" +
                        "        </xsd:sequence>\n" +
                        "      </xsd:extension>\n" +
                        "    </xsd:complexContent>\n" +
                        "  </xsd:complexType>\n" +
                        "  <xsd:element name=\"aatams_sattag_qc_ctd_profile_map\" substitutionGroup=\"gml:AbstractFeature\" type=\"imos:aatams_sattag_qc_ctd_profile_mapType\"/>\n" +
                        "</xsd:schema>";

        when(search.searchCollections(eq(uuid)))
                .thenReturn(stac);

        when(restTemplate.getForEntity(
                anyString(),
                eq(String.class))
        ).thenReturn(ResponseEntity.ok(value));

        String result = wmsServer.createCQLFilter(uuid, request);

        assertEquals("CQL_FILTER=juld DURING 2023-01-01/2023-12-31 AND set_code=1234", result);
    }
    /**
     * Test where dateTime is a range start_xxx end_xxx
     * @throws JsonProcessingException - Not expected
     */
    @Test
    public void verifyCreateCQLRangeDateTimeWithCQL() throws JsonProcessingException {
        String uuid = "uuid1";
        String layer = "aatams_sattag_qc_ctd_profile_map";
        FeatureRequest request = FeatureRequest.builder()
                .datetime("2023-01-01/2023-12-31")
                .layerName(layer)
                .build();

        ElasticSearchBase.SearchResult<StacCollectionModel> stac = new ElasticSearchBase.SearchResult<>();
        stac.setCollections(new ArrayList<>());
        stac.getCollections().add(
                StacCollectionModel
                        .builder()
                        .links(List.of(
                                LinkModel.builder()
                                        .href("http://geoserver-123.aodn.org.au/geoserver/wfs?cql_filter=set_code=1234")
                                        .title(request.getLayerName())
                                        .aiGroup(WFS_LINK_MARKER)
                                        .build())
                        )
                        .build()
        );
        // This sample contains 1 dateTime field
        String value =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xsd:schema xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:imos=\"imos.mod\" xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"imos.mod\">\n" +
                        "  <xsd:import namespace=\"http://www.opengis.net/gml/3.2\" schemaLocation=\"https://geoserver-portal.aodn.org.au/geoserver/schemas/gml/3.2.1/gml.xsd\"/>\n" +
                        "  <xsd:complexType name=\"aatams_sattag_qc_ctd_profile_mapType\">\n" +
                        "    <xsd:complexContent>\n" +
                        "      <xsd:extension base=\"gml:AbstractFeatureType\">\n" +
                        "        <xsd:sequence>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"file_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_no\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"platform_number\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"project_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"pi_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"cycle_number\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"direction\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_centre\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"dc_reference\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_state_indicator\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"data_mode\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"inst_reference\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"wmo_inst_type\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"start_juld\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"juld_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"end_juld\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"latitude\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"longitude\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"position_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"positioning_system\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_pres_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_temp_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"profile_psal_qc\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"position\" nillable=\"true\" type=\"gml:GeometryPropertyType\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"smru_platform_code\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"wmo_platform_code\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"species_name\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"release_location\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"id\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"job_id\" nillable=\"true\" type=\"xsd:long\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"url\" nillable=\"true\" type=\"xsd:string\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"created\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"modified\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"first_indexed\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"last_indexed\" nillable=\"true\" type=\"xsd:dateTime\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"last_indexed_run\" nillable=\"true\" type=\"xsd:int\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"size\" nillable=\"true\" type=\"xsd:double\"/>\n" +
                        "          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"deleted\" nillable=\"true\" type=\"xsd:boolean\"/>\n" +
                        "        </xsd:sequence>\n" +
                        "      </xsd:extension>\n" +
                        "    </xsd:complexContent>\n" +
                        "  </xsd:complexType>\n" +
                        "  <xsd:element name=\"aatams_sattag_qc_ctd_profile_map\" substitutionGroup=\"gml:AbstractFeature\" type=\"imos:aatams_sattag_qc_ctd_profile_mapType\"/>\n" +
                        "</xsd:schema>";

        when(search.searchCollections(eq(uuid)))
                .thenReturn(stac);

        when(restTemplate.getForEntity(
                anyString(),
                eq(String.class))
        ).thenReturn(ResponseEntity.ok(value));

        String result = wmsServer.createCQLFilter(uuid, request);

        assertEquals("CQL_FILTER=start_juld >= 2023-01-01 AND end_juld <= 2023-12-31 AND set_code=1234", result);
    }
}
