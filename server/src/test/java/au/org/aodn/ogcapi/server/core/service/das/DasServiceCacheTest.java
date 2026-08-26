package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.http.RecordingSseConnector;
import au.org.aodn.ogcapi.server.core.util.DasSseFrames;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the cache in front of the cloud-optimised size estimate: a repeat of the same subset is
 * answered without calling DAS, a different subset is not, and a failure leaves nothing behind.
 * The other DAS tests build the service with new, which means no proxy and no caching, so caching
 * only shows up with a Spring context around the bean. What is under test here is the annotation
 * and its key, not EhCache, so a plain ConcurrentMapCacheManager stands in for the real one.
 */
public class DasServiceCacheTest {

    private static final String HOST = "http://localhost:5000";

    private static final String HEARTBEAT_FRAME = """
            event: processing
            data: {"status":"processing","message":"Processing your request..."}

            """;

    private static final String RESULT_FRAME = """
            event: result
            data: {"status":"completed","data":{"estimated_output_bytes":123}}

            """;

    private static final String OTHER_RESULT_FRAME = """
            event: result
            data: {"status":"completed","data":{"estimated_output_bytes":456}}

            """;

    private static final String ERROR_FRAME = """
            event: error
            data: {"status":"error","message":"boom"}

            """;

    /**
     * proxyTargetClass matches what Spring Boot does by default. DasService implements
     * ApplicationInfo, so a JDK proxy would only expose that interface and the service could not
     * be looked up, or injected into RestServices, by its own type.
     */
    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class CachingContext {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.CLOUD_OPTIMISED_ESTIMATE);
        }

        @Bean
        public RecordingSseConnector connector() {
            return new RecordingSseConnector();
        }

        @Bean
        public DasService dasService(RecordingSseConnector connector) {
            // A null infoPath keeps the constructor's info query from making a request.
            DasProperties properties = new DasProperties(HOST, null, "test-secret", null,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));
            WebClient webClient = WebClient.builder()
                    .clientConnector(connector)
                    .baseUrl(HOST)
                    .build();
            return new DasService(properties, new RestTemplate(), webClient, new ObjectMapper());
        }
    }

    private AnnotationConfigApplicationContext context;
    private DasService dasService;
    private RecordingSseConnector connector;

    @BeforeEach
    public void setUp() {
        context = new AnnotationConfigApplicationContext(CachingContext.class);
        dasService = context.getBean(DasService.class);
        connector = context.getBean(RecordingSseConnector.class);
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    /**
     * A new map each time, the way SubsetParametersUtils builds one per request, so the tests
     * show the key matching on content rather than on the same instance coming back.
     */
    private static Map<String, String> parameters(String outputFormat) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("uuid", "test-uuid");
        parameters.put("key", "a.zarr");
        parameters.put("start_date", "2020-01-01");
        parameters.put("end_date", "2020-12-31");
        parameters.put("multi_polygon", "non-specified");
        parameters.put("output_format", outputFormat);
        return parameters;
    }

    /**
     * A distinct callback instance each call, the way RestServices passes a new
     * session::probeClient per request. Reusing one constant here would hide a key that wrongly
     * included the callback, because that key would still match on the second call.
     */
    private static DasSseFrames.FrameCallback freshCallback() {
        return new AtomicInteger()::incrementAndGet;
    }

    private String estimate(Map<String, String> parameters, DasSseFrames.FrameCallback onHeartbeat) {
        return dasService.estimateCloudOptimisedDownloadSize("test-uuid", parameters, onHeartbeat);
    }

    @Test
    public void testRepeatedEstimateIsServedFromCache() {
        connector.respondWith(List.of(RESULT_FRAME));

        String first = estimate(parameters("netcdf"), freshCallback());
        String second = estimate(parameters("netcdf"), freshCallback());

        assertEquals("{\"estimated_output_bytes\":123}", first);
        assertEquals(first, second, "The repeat returns the same estimate");
        assertEquals(1, connector.requests(), "The repeat is answered from the cache, not by data-access-service");
    }

    @Test
    public void testDifferentParametersStillCallDas() {
        connector.respondWith(List.of(RESULT_FRAME));
        estimate(parameters("netcdf"), freshCallback());

        connector.respondWith(List.of(OTHER_RESULT_FRAME));
        String second = estimate(parameters("csv"), freshCallback());

        assertEquals("{\"estimated_output_bytes\":456}", second);
        assertEquals(2, connector.requests(), "A different output format is a different subset, so a different key");
    }

    @Test
    public void testFailedEstimateIsNotCached() {
        connector.respondWith(List.of(ERROR_FRAME));
        assertThrows(RuntimeException.class,
                () -> estimate(parameters("netcdf"), freshCallback()));

        connector.respondWith(List.of(RESULT_FRAME));
        String retry = estimate(parameters("netcdf"), freshCallback());

        assertEquals("{\"estimated_output_bytes\":123}", retry);
        assertEquals(2, connector.requests(), "A failure caches nothing, so the retry reaches data-access-service");
    }

    /**
     * The callback is a new lambda per request, so it has to stay out of the key. A hit that
     * still ran the body, or a key that included the callback, would both show up here.
     */
    @Test
    public void testCacheHitSkipsTheStreamAndItsHeartbeats() {
        connector.respondWith(List.of(HEARTBEAT_FRAME, RESULT_FRAME));

        AtomicInteger heartbeats = new AtomicInteger();
        estimate(parameters("netcdf"), heartbeats::incrementAndGet);
        assertEquals(1, heartbeats.get(), "The first estimate reads the DAS stream");

        estimate(parameters("netcdf"), heartbeats::incrementAndGet);
        assertEquals(1, heartbeats.get(), "A cache hit never opens a stream, so nothing heartbeats");
    }
}
