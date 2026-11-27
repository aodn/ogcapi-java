package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;

import java.util.List;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES;
import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WMS_LAYERS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verify @Retryable on CacheWarm.warm*GetCapabilities retries (maxAttempts = 5), that the matching
 * @Recover method (WMS vs WFS share a signature, so it must be named explicitly) swallows the final
 * failure so the scheduled warm loop can continue, and that production config actually enables retry.
 */
@SpringJUnitConfig(CacheWarmRetryTest.Cfg.class)
class CacheWarmRetryTest {

    static final String URL = "https://example.invalid/geoserver/ows";
    static final String OTHER_URL = "https://other.invalid/geoserver/ows";

    @Configuration
    @EnableCaching
    @EnableRetry
    static class Cfg {
        // Caching is on like in production, so CacheWarm has both the cache and the retry advice
        @Bean CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(GET_CAPABILITIES_WFS_FEATURE_TYPES, GET_CAPABILITIES_WMS_LAYERS);
        }

        @Bean CacheWarm cacheWarm(WmsServer wmsServer, WfsServer wfsServer) {
            // spy so the test can check which @Recover method is invoked
            return spy(new CacheWarm(wmsServer, wfsServer, mock(CacheNoLandGeometry.class), mock(GeometryUtils.class)));
        }
    }

    @Autowired CacheWarm cacheWarm;
    @MockitoBean WfsServer wfsServer;
    @MockitoBean WmsServer wmsServer;

    @BeforeEach
    void resetSpy() {
        clearInvocations((CacheWarm) AopTestUtils.getTargetObject(cacheWarm));
    }

    @Test
    void wfsRetriesUntilMaxAttemptsThenRecovers() {
        when(wfsServer.fetchCapabilitiesFeatureTypesByUrl(anyString())).thenThrow(new RuntimeException("GeoServer down"));

        assertDoesNotThrow(() -> cacheWarm.warmWfsGetCapabilities(URL), "@Recover should swallow the final failure");
        verify(wfsServer, times(5)).fetchCapabilitiesFeatureTypesByUrl(URL);

        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        verify(target).recoverWfsGetCapabilities(any(RuntimeException.class), eq(URL));
        verify(target, never()).recoverWmsGetCapabilities(any(), any());
    }

    @Test
    void productionConfigEnablesRetry() {
        // This test wires @EnableRetry in its own Cfg, so guard that production config does too
        assertNotNull(CacheConfig.class.getAnnotation(EnableRetry.class), "CacheConfig must be annotated with @EnableRetry");
    }

    @Test
    void wfsStopsRetryingOnceSuccessful() {
        when(wfsServer.fetchCapabilitiesFeatureTypesByUrl(anyString()))
                .thenThrow(new RuntimeException("GeoServer down"))
                .thenReturn(List.of());

        cacheWarm.warmWfsGetCapabilities(URL);
        verify(wfsServer, times(2)).fetchCapabilitiesFeatureTypesByUrl(URL);

        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        verify(target, never()).recoverWfsGetCapabilities(any(), any());
        verify(target, never()).recoverWmsGetCapabilities(any(), any());
    }

    @Test
    void wmsStopsRetryingOnceSuccessful() {
        when(wmsServer.fetchCapabilitiesLayersByUrl(anyString()))
                .thenThrow(new RuntimeException("GeoServer down"))
                .thenReturn(List.of());

        cacheWarm.warmWmsGetCapabilities(URL);
        verify(wmsServer, times(2)).fetchCapabilitiesLayersByUrl(URL);

        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        verify(target, never()).recoverWmsGetCapabilities(any(), any());
        verify(target, never()).recoverWfsGetCapabilities(any(), any());
    }

    @Test
    void wmsRetriesUntilMaxAttemptsThenRecovers() {
        when(wmsServer.fetchCapabilitiesLayersByUrl(anyString())).thenThrow(new RuntimeException("GeoServer down"));

        assertDoesNotThrow(() -> cacheWarm.warmWmsGetCapabilities(URL), "@Recover should swallow the final failure");
        verify(wmsServer, times(5)).fetchCapabilitiesLayersByUrl(URL);

        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        verify(target).recoverWmsGetCapabilities(any(RuntimeException.class), eq(URL));
        verify(target, never()).recoverWfsGetCapabilities(any(), any());
    }

    @Test
    void scheduledWfsWarmRetriesThroughSelfAndContinuesWithNextUrl() {
        when(wfsServer.createCapabilitiesQueryUrl(anyString())).thenAnswer(i -> i.getArgument(0) + "?REQUEST=GetCapabilities");
        when(wfsServer.fetchCapabilitiesFeatureTypesByUrl(URL + "?REQUEST=GetCapabilities")).thenThrow(new RuntimeException("GeoServer down"));
        when(wfsServer.fetchCapabilitiesFeatureTypesByUrl(OTHER_URL + "?REQUEST=GetCapabilities")).thenReturn(List.of());

        // Call the scheduled method on the target, not the proxy, so the retry only works through self, like the scheduler
        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        List<String> urls = target.getWfsCapabilitiesUrls;
        target.getWfsCapabilitiesUrls = List.of(URL, OTHER_URL);
        try {
            assertDoesNotThrow(target::evictAndWarmWfsCache);
        } finally {
            target.getWfsCapabilitiesUrls = urls;
        }

        // The failing URL is tried 5 times, then recovered, and the next URL is still warmed
        verify(wfsServer, times(5)).fetchCapabilitiesFeatureTypesByUrl(URL + "?REQUEST=GetCapabilities");
        verify(target).recoverWfsGetCapabilities(any(RuntimeException.class), eq(URL + "?REQUEST=GetCapabilities"));
        verify(wfsServer, times(1)).fetchCapabilitiesFeatureTypesByUrl(OTHER_URL + "?REQUEST=GetCapabilities");
    }

    @Test
    void scheduledWmsWarmRetriesThroughSelf() {
        when(wmsServer.fetchCapabilitiesLayersByUrl(anyString()))
                .thenThrow(new RuntimeException("GeoServer down"))
                .thenReturn(List.of());

        CacheWarm target = AopTestUtils.getTargetObject(cacheWarm);
        List<String> urls = target.getWmsCapabilitiesUrls;
        target.getWmsCapabilitiesUrls = List.of(URL);
        try {
            assertDoesNotThrow(target::evictAndWarmWmsCache);
        } finally {
            target.getWmsCapabilitiesUrls = urls;
        }

        verify(wmsServer, times(2)).fetchCapabilitiesLayersByUrl(URL);
        verify(target, never()).recoverWmsGetCapabilities(any(), any());
    }
}
