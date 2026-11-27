package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
import au.org.aodn.ogcapi.server.core.service.CacheNoLandGeometry;
import au.org.aodn.ogcapi.server.core.service.CacheWarm;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.StacCollectionModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the cache in front of the WFS GetCapabilities call: the key is the request that is sent to
 * GeoServer, so links that end up as the same request share an entry, and CacheWarm warms and evicts
 * that same entry. WfsServerTest builds the server with new, which means no proxy and no caching, so
 * this needs a Spring context around the beans. What is under test here is the annotations and their
 * keys, not EhCache, so a plain ConcurrentMapCacheManager stands in for the real one.
 */
public class WfsServerCacheTest {

    private static final String QUERY = "?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetCapabilities";

    // One of the servers that CacheWarm warms
    private static final String AODN_OWS = "geoserver-123.aodn.org.au/geoserver/ows";
    private static final String AODN_REQUEST = "https://" + AODN_OWS + QUERY;

    // The requests for all servers that CacheWarm warms
    private static final Set<String> WARM_REQUESTS = Set.of(
            AODN_REQUEST,
            "https://geoserver.imas.utas.edu.au/geoserver/ows" + QUERY,
            "https://www.cmar.csiro.au/geoserver/ows" + QUERY,
            "https://geoserver.apps.aims.gov.au/aims/ows" + QUERY,
            "https://data.aad.gov.au/geoserver/underway/ows" + QUERY
    );

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class CachingContext {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES,
                    CacheConfig.GET_CAPABILITIES_WMS_LAYERS,
                    CacheConfig.DOWNLOADABLE_FIELDS
            );
        }

        @Bean
        public Search search() {
            return mock(Search.class);
        }

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public WfsServer wfsServer(Search search, RestTemplate restTemplate) {
            // Same as application.yaml
            Map<String, String> capabilities = new LinkedHashMap<>();
            capabilities.put("SERVICE", "WFS");
            capabilities.put("VERSION", "2.0.0");
            capabilities.put("REQUEST", "GetCapabilities");

            WfsDefaultParam param = new WfsDefaultParam();
            param.setCapabilities(capabilities);

            return new WfsServer(search, restTemplate, new RestTemplateUtils(restTemplate), HttpEntity.EMPTY, param);
        }

        @Bean
        public CacheWarm cacheWarm(WfsServer wfsServer) {
            // The WFS warm up uses the WFS server only
            return new CacheWarm(mock(WmsServer.class), wfsServer, mock(CacheNoLandGeometry.class), mock(GeometryUtils.class));
        }
    }

    private AnnotationConfigApplicationContext context;
    private WfsServer wfsServer;
    private CacheWarm cacheWarm;
    private Search search;
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        context = new AnnotationConfigApplicationContext(CachingContext.class);
        wfsServer = context.getBean(WfsServer.class);
        cacheWarm = context.getBean(CacheWarm.class);
        search = context.getBean(Search.class);
        restTemplate = context.getBean(RestTemplate.class);
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    /**
     * A collection with one WFS link, the title matches the layer "test:layer"
     */
    private void givenCollection(String id, String href) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = new ElasticSearchBase.SearchResult<>();
        result.setCollections(List.of(
                StacCollectionModel.builder()
                        .links(List.of(LinkModel.builder()
                                .title("test:layer")
                                .aiGroup(WFS_LINK_MARKER)
                                .href(href)
                                .build()))
                        .build()
        ));
        when(search.searchCollections(eq(id))).thenReturn(result);
    }

    /**
     * Any GeoServer answers with one layer "test:layer", the layer title shows which answer was returned
     */
    private void givenGeoServerAnswers(String layerTitle) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <wfs:WFS_Capabilities xmlns:wfs="http://www.opengis.net/wfs/2.0" xmlns="http://www.opengis.net/wfs/2.0">
                            <FeatureTypeList>
                                <FeatureType>
                                    <Name>test:layer</Name>
                                    <Title>%s</Title>
                                </FeatureType>
                            </FeatureTypeList>
                        </wfs:WFS_Capabilities>
                        """.formatted(layerTitle)));
    }

    /**
     * @return The title of the only layer found for the collection
     */
    private String lookup(String id) {
        List<FeatureTypeInfo> featureTypes = wfsServer.getCapabilitiesFeatureTypes(id, null);
        assertEquals(1, featureTypes.size(), "One layer for " + id);
        return featureTypes.get(0).getTitle();
    }

    private void verifyGeoServerCalls(int total) {
        verify(restTemplate, times(total)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
    }

    private void verifyGeoServerCalls(int count, String url) {
        verify(restTemplate, times(count)).exchange(eq(url), eq(HttpMethod.GET), any(), eq(String.class));
    }

    private Set<Object> cacheKeys() {
        ConcurrentMapCache cache = (ConcurrentMapCache) context.getBean(CacheManager.class)
                .getCache(CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES);
        assertNotNull(cache);
        return cache.getNativeCache().keySet();
    }

    /**
     * Without the proxies nothing is cached, and the tests below would not test the annotations
     */
    @Test
    public void testBeansAreProxied() {
        assertTrue(AopUtils.isAopProxy(wfsServer), "WfsServer is called via the cache proxy");
        assertTrue(AopUtils.isAopProxy(cacheWarm), "CacheWarm is called via the cache proxy");
    }

    @Test
    public void testLinksWithDifferentQueryStringsCallGeoServerOnce() {
        givenGeoServerAnswers("v1");
        givenCollection("a", "https://" + AODN_OWS + "?service=WFS&version=1.0.0&request=GetFeature&typeName=test:layer");
        givenCollection("b", "https://" + AODN_OWS + "?service=WFS&request=GetCapabilities");
        givenCollection("c", "https://" + AODN_OWS);
        givenCollection("d", "http://" + AODN_OWS);

        for (String id : List.of("a", "b", "c", "d")) {
            assertEquals("v1", lookup(id));
        }

        verifyGeoServerCalls(1);
        verifyGeoServerCalls(1, AODN_REQUEST);
        assertEquals(Set.of(AODN_REQUEST), cacheKeys(), "The key is the request URL");
    }

    @Test
    public void testDifferentServerOrPathStillCallsGeoServer() {
        givenGeoServerAnswers("v1");
        givenCollection("a", "https://geoserver.example.com/geoserver/ows?typeName=test:layer");
        givenCollection("b", "https://geoserver.example.com/geoserver/workspace/ows?typeName=test:layer");
        givenCollection("c", "https://other.example.com/geoserver/ows?typeName=test:layer");
        givenCollection("d", "https://geoserver.example.com:8443/geoserver/ows?typeName=test:layer");

        for (String id : List.of("a", "b", "c", "d")) {
            lookup(id);
        }

        verifyGeoServerCalls(4);
        verifyGeoServerCalls(1, "https://geoserver.example.com/geoserver/ows" + QUERY);
        verifyGeoServerCalls(1, "https://geoserver.example.com/geoserver/workspace/ows" + QUERY);
        verifyGeoServerCalls(1, "https://other.example.com/geoserver/ows" + QUERY);
        verifyGeoServerCalls(1, "https://geoserver.example.com:8443/geoserver/ows" + QUERY);
    }

    @Test
    public void testLookupUsesTheEntryFromWarmUp() {
        givenGeoServerAnswers("v1");
        cacheWarm.evictAndWarmWfsCache();

        verifyGeoServerCalls(WARM_REQUESTS.size());
        assertEquals(WARM_REQUESTS, cacheKeys(), "The keys are the request URLs");

        givenCollection("a", "http://" + AODN_OWS + "?service=WFS&request=GetFeature&typeName=test:layer");
        assertEquals("v1", lookup("a"));

        verifyGeoServerCalls(WARM_REQUESTS.size());
        assertEquals(WARM_REQUESTS, cacheKeys(), "The lookup adds no entry");
    }

    /**
     * If evict used another key than the lookup, the warm-up would be answered from the old entry
     */
    @Test
    public void testWarmUpReplacesTheEntryFromLookup() {
        givenCollection("a", "https://" + AODN_OWS + "?service=WFS&request=GetFeature&typeName=test:layer");
        givenGeoServerAnswers("v1");
        assertEquals("v1", lookup("a"));
        verifyGeoServerCalls(1, AODN_REQUEST);

        givenGeoServerAnswers("v2");
        cacheWarm.evictAndWarmWfsCache();
        verifyGeoServerCalls(2, AODN_REQUEST);

        assertEquals("v2", lookup("a"));
        verifyGeoServerCalls(2, AODN_REQUEST);
    }
}
