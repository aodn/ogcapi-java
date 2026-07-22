package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code @Cacheable} on {@link DasTilerService#getProducts()}, which sits on the
 * visual-tile hot path via {@code isProductInCollection}.
 * <p>
 * Unlike the rest of the DasTilerService tests this one needs a real (tiny) Spring context: caching
 * is proxy-based, so a plainly-constructed instance would pass these assertions whether the wiring
 * is right or not. In particular {@code productsForCollection} reaches {@code getProducts()} through
 * the {@code self} proxy reference — swap that back to a bare {@code this} call and the caching
 * silently stops working, which is exactly what {@link #testProductListIsFetchedOncePerTtl} catches.
 */
public class DasTilerServiceCachingTest {

    private static final DasProperties DAS_PROPERTIES = new DasProperties(
            "http://localhost:5000", "test-secret", "internal-secret",
            Duration.ofSeconds(5), Duration.ofSeconds(30));

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.CACHE_TILER_PRODUCTS);
        }

        @Bean
        public RestTemplate httpClient() {
            return mock(RestTemplate.class);
        }

        @Bean
        public DasTilerService dasTilerService(RestTemplate httpClient) {
            return new DasTilerService(DAS_PROPERTIES, httpClient, new ObjectMapper());
        }
    }

    private AnnotationConfigApplicationContext context;
    private RestTemplate httpClient;
    private DasTilerService service;

    @BeforeEach
    public void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        httpClient = context.getBean(RestTemplate.class);
        service = context.getBean(DasTilerService.class);
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    private JsonNode productList() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "p1").put("metadata_uuid", "uuid-a"))
                .add(mapper.createObjectNode().put("id", "p2").put("metadata_uuid", "uuid-b"));
    }

    @Test
    public void testProductListIsFetchedOncePerTtl() {
        when(httpClient.getForObject(anyString(), eq(JsonNode.class))).thenReturn(productList());

        // The tile hot path: a membership check per tile must not mean a /products call per tile
        assertTrue(service.isProductInCollection("uuid-a", "p1"));
        assertTrue(service.isProductInCollection("uuid-a", "p1"));
        assertFalse(service.isProductInCollection("uuid-a", "p2"));
        assertEquals(1, service.productsForCollection("uuid-b").size());
        assertEquals(2, service.getProducts().size());

        verify(httpClient, times(1)).getForObject(anyString(), eq(JsonNode.class));
    }

    @Test
    public void testUpstreamFailureIsNotCached() {
        when(httpClient.getForObject(anyString(), eq(JsonNode.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(productList());

        // An outage must not stick for the whole TTL — @Cacheable stores return values, not throws
        assertThrows(DasUpstreamException.class, () -> service.isProductInCollection("uuid-a", "p1"));
        assertTrue(service.isProductInCollection("uuid-a", "p1"));

        verify(httpClient, times(2)).getForObject(anyString(), eq(JsonNode.class));
    }
}
