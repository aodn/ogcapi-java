package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.service.dda.DdaProperties;
import au.org.aodn.ogcapi.server.core.service.dda.DdaService;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ApplicationInfoTest {

    private static final DdaProperties PROPERTIES = new DdaProperties("http://dda", "api/v1/ml/manage/info");

    private static final Map<String, Map<?, ?>> INFO = Map.of(
            "application", Map.of("name", "dda", "version", "1.0", "description", "desc"));

    @SuppressWarnings("unchecked")
    private static OngoingStubbing<ResponseEntity<Object>> whenExchange(RestTemplate template) {
        return when(template.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)));
    }

    @Test
    public void testNoCallAtStartup() {
        RestTemplate template = mock(RestTemplate.class);

        new DdaService(PROPERTIES, template);

        verifyNoInteractions(template);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEveryCallFetchesAgain() {
        Map<String, Map<?, ?>> newInfo = Map.of(
                "application", Map.of("name", "dda", "version", "1.1", "description", "desc"));

        RestTemplate template = mock(RestTemplate.class);
        whenExchange(template)
                .thenReturn(ResponseEntity.ok(INFO))
                .thenReturn(ResponseEntity.ok(newInfo));

        DdaService service = new DdaService(PROPERTIES, template);

        // 1. First call gets the version that runs now
        assertEquals("1.0", service.getAppInfo().get("application").get("version"));
        // 2. Service redeployed, the next call must show the new version, nothing is cached
        assertEquals("1.1", service.getAppInfo().get("application").get("version"));

        verify(template, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testServiceDownReturnEmptyAndRetry() {
        RestTemplate template = mock(RestTemplate.class);
        whenExchange(template)
                .thenThrow(new ResourceAccessException("Connect timed out"))
                .thenReturn(ResponseEntity.ok(INFO));

        DdaService service = new DdaService(PROPERTIES, template);

        // 1. Service down, empty map and no exception
        assertTrue(service.getAppInfo().isEmpty());
        // 2. Service up again, the next call tries again
        assertEquals(INFO, service.getAppInfo());

        verify(template, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }
}
