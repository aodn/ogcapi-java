package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.cache.Caching;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boots the REAL Spring {@code @Cacheable} CGLIB proxy over {@link DasTilerService} — not a
 * Mockito mock of the service, and not a hand-constructed instance like {@link DasTilerServiceTest}
 * — to prove the {@code cv}-staleness check in {@code getVisualTile} actually re-runs on a cache
 * hit. A plain unit test can't catch this class of bug: it never goes through the proxy that
 * {@code @Cacheable} depends on, so it can't tell "check runs every call" apart from "check runs
 * only on the first call, then the cache silently serves stale bytes forever."
 * <p>
 * Uses its own tiny EhCache manager (heap-only, unique random URI per test) instead of the real
 * {@link CacheConfig} bean, since that class also declares WMS/WFS cache-warming beans unrelated
 * to this test and a shared on-disk EhCache URI that could collide with other test classes in the
 * same JVM fork.
 */
public class DasTilerServiceCachingTest {

    private static final String HOST = "http://localhost:5000";
    private static final String PRODUCT_ID = "model_sea_level_anomaly_gridded_realtime:gsla";

    @Configuration
    @EnableCaching
    static class CachingTestConfig {
    }

    private AnnotationConfigApplicationContext context;
    private RestTemplate httpClient;
    private DasTilerService service;

    @BeforeEach
    public void setUp() {
        httpClient = mock(RestTemplate.class);

        org.ehcache.config.Configuration ehConfig = ConfigurationBuilder.newConfigurationBuilder()
                .withCache(CacheConfig.CACHE_TILER_TILE, CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class, ResourcePoolsBuilder.heap(50))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(24))))
                .withCache(CacheConfig.CACHE_TILER_MANIFEST, CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class, ResourcePoolsBuilder.heap(50))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5))))
                .withCache(CacheConfig.CACHE_TILER_PRODUCTS, CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class, ResourcePoolsBuilder.heap(50))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5))))
                .build();

        EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider();
        javax.cache.CacheManager jCacheManager = provider.getCacheManager(
                URI.create("das-tiler-test-" + UUID.randomUUID()), ehConfig);

        context = new AnnotationConfigApplicationContext();
        context.register(CachingTestConfig.class);
        context.registerBean("cacheManager", JCacheCacheManager.class, () -> new JCacheCacheManager(jCacheManager));
        context.registerBean(DASConfig.class, () -> new DASConfig(HOST, "test-secret", "internal-secret"));
        context.registerBean(DasTilerService.class);
        // DasTilerService intentionally does not @Autowire its RestTemplate (see its own
        // javadoc: that would let it silently pick up the app-wide 20-minute-timeout bean by
        // type match). context.getBean(...) below returns the @Cacheable CGLIB proxy, whose
        // method calls delegate to a *separate* underlying target instance — setting a field on
        // the proxy reference after refresh() doesn't reach that target, so the mock must be
        // wired onto the raw instance before its @PostConstruct init() runs (which is exactly
        // when real DI would happen), via a BeanPostProcessor registered ahead of refresh().
        context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DasTilerService dasTilerService) {
                    dasTilerService.httpClient = httpClient;
                }
                return bean;
            }
        });
        context.refresh();

        service = context.getBean(DasTilerService.class);
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    private HttpHeaders imageHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return headers;
    }

    private JsonNode manifestWithCv(String cv) {
        return new ObjectMapper().createObjectNode().put("cache_version", cv);
    }

    @Test
    public void repeatedIdenticalRequestIsServedFromCacheWithoutCallingDasAgain() {
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(manifestWithCv("cv1")));
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("tile-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null, "cv1");
        service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null, "cv1");

        // Only the first call should have reached the network for the tile bytes themselves.
        verify(httpClient, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap());
    }

    @Test
    public void staleCvOnAnAlreadyCachedTileStillReturns410NotStaleBytes() {
        // First request: manifest says cv1, matches — tile is fetched for real and cached.
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(manifestWithCv("cv1")));
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class), anyMap()))
                .thenReturn(new ResponseEntity<>("original-bytes".getBytes(), imageHeaders(), HttpStatus.OK));

        DasTilerService.DasTileResult first =
                service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null, "cv1");
        assertArrayEquals("original-bytes".getBytes(), first.body());

        // DAS is redeployed with new rendering code: the manifest now advertises cv2. In
        // production this becomes visible once CACHE_TILER_MANIFEST's own 5-minute TTL expires;
        // evict it directly here rather than waiting, to simulate that window having passed.
        context.getBean(org.springframework.cache.CacheManager.class)
                .getCache(CacheConfig.CACHE_TILER_MANIFEST)
                .clear();
        reset(httpClient);
        when(httpClient.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(manifestWithCv("cv2")));

        // Identical tile request as before (same product/date/z/x/y/ext/cv) — a cache HIT on
        // CACHE_TILER_TILE. Before splitting the cv check out of the @Cacheable method, Spring's
        // proxy would return the cached "original-bytes" here without ever re-checking cv.
        DasUpstreamException ex = assertThrows(DasUpstreamException.class,
                () -> service.getVisualTile(PRODUCT_ID, "2024-01-01", 2, 1, 1, "png", null, null, "cv1"));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }
}
