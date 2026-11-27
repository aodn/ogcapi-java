package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES;
import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WMS_LAYERS;

/**
 * Some WMS server response very slow for GetCapabilities operation, so we warm some
 * of those operation for the target server.
 */
@Slf4j
public class CacheWarm {
    // Hardcode server list as not expect to change much overtime, add more if needed, the operation is
    // heavy so we warm the cache when start
    protected List<String> getWmsCapabilitiesUrls = List.of(
            "https://data.aad.gov.au/geoserver/underway/wms",
            "https://www.cmar.csiro.au/geoserver/caab/wms",
            "https://www.cmar.csiro.au/geoserver/ereefs/wms",
            "https://www.cmar.csiro.au/geoserver/ea-be/wms",
            "https://www.cmar.csiro.au/geoserver/gsfm/wms",
            "https://www.cmar.csiro.au/geoserver/local/wms",
            "https://www.cmar.csiro.au/geoserver/mnf/wms",
            "https://www.cmar.csiro.au/geoserver/nerp/wms",
            "https://www.cmar.csiro.au/geoserver/AusSeabed/wms",
            "https://geoserver.apps.aims.gov.au/aims/wms",
            "https://geoserver.apps.aims.gov.au/reefcloud/wms",
            "https://maps.eatlas.org.au/maps/wms"
    );

    // WFS server URLs for cache warming - using /ows endpoint which supports both WMS and WFS
    protected List<String> getWfsCapabilitiesUrls = List.of(
            "https://geoserver-123.aodn.org.au/geoserver/ows",
            "https://geoserver.imas.utas.edu.au/geoserver/ows",
            "https://www.cmar.csiro.au/geoserver/ows",
            "https://geoserver.apps.aims.gov.au/aims/ows",
            "https://data.aad.gov.au/geoserver/underway/ows"
    );

    protected WmsServer wmsServer;
    protected WfsServer wfsServer;
    protected GeometryUtils geometryUtils;
    protected CacheNoLandGeometry cacheNoLandGeometry;

    @Lazy
    @Autowired
    protected CacheWarm self;

    public CacheWarm(WmsServer wmsServer,
                     WfsServer wfsServer,
                     CacheNoLandGeometry cacheNoLandGeometry,
                     GeometryUtils geometryUtils) {
        this.cacheNoLandGeometry = cacheNoLandGeometry;
        this.geometryUtils = geometryUtils;
        this.wmsServer = wmsServer;
        this.wfsServer = wfsServer;
    }

    /**
     * Scheduled task to refresh WMS cache 5 sec after starts and then every 23 hours
     */
    @Scheduled(initialDelay = 5000, fixedRate = 23 * 60 * 60 * 1000)
    public void evictAndWarmWmsCache() {
        // Evict and warm WMS cache entries one by one
        getWmsCapabilitiesUrls.forEach(url -> {
            self.evictWmsGetCapabilities(url);
            self.warmWmsGetCapabilities(url);
        });
    }

    /**
     * Scheduled task to refresh WFS cache 10 sec after starts and then every 23 hours
     */
    @Scheduled(initialDelay = 10000, fixedRate = 23 * 60 * 60 * 1000)
    public void evictAndWarmWfsCache() {
        // Evict and warm WFS cache entries one by one
        getWfsCapabilitiesUrls.forEach(url -> {
            // The cache key is the request URL, must be the same one as the lookup in WfsServer
            String capabilitiesUrl = wfsServer.createCapabilitiesQueryUrl(url);
            self.evictWfsGetCapabilities(capabilitiesUrl);
            self.warmWfsGetCapabilities(capabilitiesUrl);
        });
    }

    @Scheduled(initialDelay = 1000, fixedRate = 23 * 60 * 60 * 1000)
    public void keepWarmNoLandGeometryAndGeometryUtil() {
        Map<String, StacCollectionModel> value = cacheNoLandGeometry.getAllNoLandGeometry();
        value.values()
                .stream()
                .filter(collection -> collection.getSummaries() != null && collection.getSummaries().getGeometryNoLand() != null)
                .forEach(collection -> geometryUtils.readCachedGeometry(collection.getSummaries().getGeometryNoLand()));
    }


    // ==================== WMS Cache Methods ====================

    @CacheEvict(value = GET_CAPABILITIES_WMS_LAYERS, key = "#key")
    public void evictWmsGetCapabilities(String key) {
        // @CacheEvict handles the eviction
        log.info("Evicting WMS cache {} for key {}", GET_CAPABILITIES_WMS_LAYERS, key);
    }

    /**
     * Must let the exception propagate, otherwise @Retryable never sees a failure to retry.
     * After the last attempt fails, recoverWmsGetCapabilities is called instead. The recover method
     * must be named explicitly: WMS/WFS recover methods share a signature, so matching by type is ambiguous.
     */
    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 1000), recover = "recoverWmsGetCapabilities")
    protected void warmWmsGetCapabilities(String url) {
        // Call and warm cache
        wmsServer.fetchCapabilitiesLayersByUrl(url);
        log.info("WMS Cache GetCapabilities warm success for {}", url);
    }

    @Recover
    protected void recoverWmsGetCapabilities(RuntimeException rte, String url) {
        log.warn("WMS Cache GetCapabilities warm failed for {} after retries, call will be slow: {}", url, rte.getMessage());
    }

    // ==================== WFS Cache Methods ====================

    @CacheEvict(value = GET_CAPABILITIES_WFS_FEATURE_TYPES, key = "#key")
    public void evictWfsGetCapabilities(String key) {
        // @CacheEvict handles the eviction
        log.info("Evicting WFS cache {} for key {}", GET_CAPABILITIES_WFS_FEATURE_TYPES, key);
    }

    /**
     * Must let the exception propagate, otherwise @Retryable never sees a failure to retry.
     * After the last attempt fails, recoverWfsGetCapabilities is called instead (named explicitly, see WMS above).
     */
    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 1000), recover = "recoverWfsGetCapabilities")
    protected void warmWfsGetCapabilities(String capabilitiesUrl) {
        // Call and warm cache
        wfsServer.fetchCapabilitiesFeatureTypesByUrl(capabilitiesUrl);
        log.info("WFS Cache GetCapabilities warm success for {}", capabilitiesUrl);
    }

    @Recover
    protected void recoverWfsGetCapabilities(RuntimeException rte, String url) {
        // Only the message: for HTTP and XML errors, the fetch already logs the stack trace on each attempt
        log.warn("WFS Cache GetCapabilities warm failed for {} after retries, call will be slow: {}", url, rte.getMessage());
    }
}
