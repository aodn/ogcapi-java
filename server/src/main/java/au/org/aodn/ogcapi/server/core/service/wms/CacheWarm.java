package au.org.aodn.ogcapi.server.core.service.wms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;

/**
 * Some WMS server response very slow for GetCapabilities operation, so we warm some
 * of those operation for the target server.
 */
@Slf4j
public class CacheWarm {
    // Hardcode server list as not expect to change much overtime, add more if needed
    protected List<String> getCapabilitiesUrls = List.of(
            "https://data.aad.gov.au/geoserver/underway/wms"
    );
    protected WmsServer wmsServer;

    @Lazy
    @Autowired
    protected CacheWarm self;

    public CacheWarm(WmsServer wmsServer) {
        this.wmsServer = wmsServer;
    }
    /**
     * Scheduled task to refresh specific keys 5 sec after starts and then every 23 hours
     */
    @Scheduled(initialDelay = 5000, fixedRate = 23 * 60 * 60 * 1000)
    public void evictSpecificCacheEntries() {
        // Evict specific keys one by one
        getCapabilitiesUrls.forEach(url -> {
            self.evictGetCapabilities(url);
            self.warmGetCapabilities(url);
        });
    }

    @CacheEvict(value = au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WMS_LAYERS, key = "#key")
    protected void evictGetCapabilities(String key){
        // @CacheEvict handles the eviction
        log.info("Evicting cache {} for key {}",
                au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WMS_LAYERS,
                key
        );
    }

    protected void warmGetCapabilities(String url) {
        try {
            // Call and warm cache
            wmsServer.fetchCapabilitiesLayersByUrl(url);
            log.info("Cache GetCapabilities warm success for {}", url);
        }
        catch(RuntimeException rte) {
            log.warn("Cache GetCapabilities warm failed for {}, call will be slow", url);
        }
    }
}
