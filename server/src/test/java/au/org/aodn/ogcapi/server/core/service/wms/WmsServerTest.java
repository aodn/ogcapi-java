package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.TestConfig;
import au.org.aodn.ogcapi.server.core.configuration.WfsWmsConfig;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.Search;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = {
                TestConfig.class,
                Config.class,
                WfsWmsConfig.class,
                WmsDefaultParam.class,
                JacksonAutoConfiguration.class,
                CacheAutoConfiguration.class}
)
public class WmsServerTest {
    // Inject the mock bean on the fly to avoid dependency issue
    // we do not need this bean's function in this test
    @MockitoBean
    protected Search search;

    @Autowired
    protected WmsServer wmsServer;

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

        List<String> urls = wmsServer.createQueryUrl("http://geoserver-123.aodn.org.au/geoserver/wms", featureRequest);

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

        List<String> urls = wmsServer.createQueryUrl("https://geoserver-123.aodn.org.au/geoserver/ncwms", featureRequest);

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
}
