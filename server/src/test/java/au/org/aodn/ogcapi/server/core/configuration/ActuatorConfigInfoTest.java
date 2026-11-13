package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.dda.DdaService;
import au.org.aodn.ogcapi.server.core.service.geonetwork.Geonetwork;
import au.org.aodn.ogcapi.server.core.service.indexer.EsIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the depService part of the info endpoint.
 */
public class ActuatorConfigInfoTest {

    private final ActuatorConfig config = new ActuatorConfig();

    private static Map<String, Map<?, ?>> info(String name, String version, String description) {
        Map<String, String> application = new HashMap<>();
        application.put("name", name);
        application.put("version", version);
        application.put("description", description);
        return Map.of("application", application);
    }

    private static ApplicationInfo service(Map<String, Map<?, ?>> info) {
        ApplicationInfo service = mock(ApplicationInfo.class);
        when(service.getAppInfo()).thenReturn(info);
        return service;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<?, ?>> depService(List<ApplicationInfo> services, Duration timeout) {
        Info.Builder builder = new Info.Builder();
        config.createInfoContributor(services, timeout).contribute(builder);
        return (Map<String, Map<?, ?>>) builder.build().get("depService");
    }

    @Test
    public void testOneCallPerServiceForOneRequest() {
        DasService das = mock(DasService.class);
        DdaService dda = mock(DdaService.class);
        EsIndexer esIndexer = mock(EsIndexer.class);
        Geonetwork geonetwork = mock(Geonetwork.class);

        when(das.getAppInfo()).thenReturn(info("data-access-service", "v1.0.8", "Data Access Service"));
        when(dda.getAppInfo()).thenReturn(info("data-discovery-ai", "v1.0.5", "Data Discovery AI"));
        when(esIndexer.getAppInfo()).thenReturn(info("es-indexer", "1.0.8", "Indexer"));
        when(geonetwork.getAppInfo()).thenReturn(info("geonetwork4-core", "1.0.9", "Geonetwork"));

        Info.Builder builder = new Info.Builder();
        config.externalApiInfoContributor(das, dda, esIndexer, geonetwork).contribute(builder);

        assertEquals(
                Map.of(
                        "data-access-service", Map.of("version", "v1.0.8", "description", "Data Access Service"),
                        "data-discovery-ai", Map.of("version", "v1.0.5", "description", "Data Discovery AI"),
                        "es-indexer", Map.of("version", "1.0.8", "description", "Indexer"),
                        "geonetwork4-core", Map.of("version", "1.0.9", "description", "Geonetwork")),
                builder.build().get("depService"));

        verify(das, times(1)).getAppInfo();
        verify(dda, times(1)).getAppInfo();
        verify(esIndexer, times(1)).getAppInfo();
        verify(geonetwork, times(1)).getAppInfo();
    }

    @Test
    public void testEveryRequestFetchesAgain() {
        ApplicationInfo das = mock(ApplicationInfo.class);
        when(das.getAppInfo())
                .thenReturn(info("data-access-service", "v1.0.7", "Data Access Service"))
                .thenReturn(info("data-access-service", "v1.0.8", "Data Access Service"));

        // 1. Version that runs now
        assertEquals("v1.0.7", depService(List.of(das), ActuatorConfig.INFO_TIMEOUT).get("data-access-service").get("version"));
        // 2. Service redeployed, the next request shows the new version without restart
        assertEquals("v1.0.8", depService(List.of(das), ActuatorConfig.INFO_TIMEOUT).get("data-access-service").get("version"));
    }

    @Test
    public void testServiceDownIsLeftOut() {
        ApplicationInfo down = service(Collections.emptyMap());
        ApplicationInfo broken = mock(ApplicationInfo.class);
        when(broken.getAppInfo()).thenThrow(new IllegalStateException("broken"));
        ApplicationInfo up = service(info("es-indexer", "1.0.8", "Indexer"));

        assertEquals(
                Map.of("es-indexer", Map.of("version", "1.0.8", "description", "Indexer")),
                depService(List.of(down, broken, up), ActuatorConfig.INFO_TIMEOUT));
    }

    @Test
    public void testMissingValueDoesNotBreakOtherServices() {
        ApplicationInfo noDescription = service(info("es-indexer", "1.0.8", null));
        ApplicationInfo noName = service(info(null, "1.0.0", "No name"));
        ApplicationInfo complete = service(info("geonetwork4-core", "1.0.9", "Geonetwork"));

        assertEquals(
                Map.of(
                        "es-indexer", Map.of("version", "1.0.8"),
                        "geonetwork4-core", Map.of("version", "1.0.9", "description", "Geonetwork")),
                depService(List.of(noDescription, noName, complete), ActuatorConfig.INFO_TIMEOUT));
    }

    @Test
    public void testSlowServiceIsLeftOut() {
        CountDownLatch release = new CountDownLatch(1);
        ApplicationInfo slow = mock(ApplicationInfo.class);
        when(slow.getAppInfo()).thenAnswer(invocation -> {
            release.await();
            return info("data-access-service", "v1.0.8", "Data Access Service");
        });
        ApplicationInfo fast = service(info("es-indexer", "1.0.8", "Indexer"));

        try {
            // The request must come back near the timeout, not wait for the slow service
            Map<String, Map<?, ?>> result = assertTimeout(
                    Duration.ofSeconds(5),
                    () -> depService(List.of(slow, fast), Duration.ofMillis(300)));

            assertEquals(Map.of("es-indexer", Map.of("version", "1.0.8", "description", "Indexer")), result);
        } finally {
            release.countDown();
        }
    }
}
