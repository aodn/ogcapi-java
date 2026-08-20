package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.http.RecordingSseConnector;
import au.org.aodn.ogcapi.server.core.service.das.DasProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DAS credentials are attached by the client beans themselves, not per call, so this is where
 * that wiring is covered, including the template GeoServer shares never carrying them.
 */
public class ConfigTest {

    private static final DasProperties DAS_PROPERTIES = new DasProperties(
            "http://localhost:5000", null, "test-secret", "internal-secret",
            Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

    private final Config config = new Config();

    /**
     * Runs a template's interceptor chain over a bare request and returns the headers they set.
     * Interceptors only touch headers before delegating, so a stubbed execution is enough.
     */
    private static HttpHeaders headersAfterInterceptors(RestTemplate template) throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:5000/probe"));
        for (ClientHttpRequestInterceptor interceptor : template.getInterceptors()) {
            interceptor.intercept(request, new byte[0],
                    (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        }
        return request.getHeaders();
    }

    @Test
    public void testDasTemplateAttachesApiKeyButNotAccept() throws IOException {
        HttpHeaders headers = headersAfterInterceptors(config.createDasRestTemplate(DAS_PROPERTIES));

        assertEquals("test-secret", headers.getFirst("X-API-KEY"));
        assertEquals("internal-secret", headers.getFirst("x-internal-das-header-secret"));
        assertTrue(headers.getAccept().isEmpty(),
                "the same client fetches JSON and binary tiles, so Accept is left to each call");
    }

    /**
     * A WebClient has no interceptors to inspect, so the credentials are read back off a request
     * it actually made, through a connector that records one.
     */
    @Test
    public void testDasSseClientAttachesTheSameCredentials() {
        RecordingSseConnector connector = new RecordingSseConnector();
        config.createDasSseWebClient(DAS_PROPERTIES, new ObjectMapper())
                .mutate()
                .clientConnector(connector)
                .build()
                .get()
                .uri("/probe")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertEquals("test-secret", connector.headers().getFirst("X-API-KEY"));
        assertEquals("internal-secret", connector.headers().getFirst("x-internal-das-header-secret"));
    }

    @Test
    public void testApplicationWideTemplateCarriesNoDasCredentials() throws IOException {
        RestTemplate restTemplate = config.createRestTemplate();

        assertTrue(restTemplate.getInterceptors().isEmpty());
        HttpHeaders headers = headersAfterInterceptors(restTemplate);
        assertNull(headers.getFirst("X-API-KEY"),
                "GeoServer shares this template — the DAS secret must not ride along");
        assertNull(headers.getFirst("x-internal-das-header-secret"));
    }

    @Test
    public void testInternalSecretOmittedWhenNotConfigured() throws IOException {
        DasProperties noInternal = new DasProperties(
                "http://localhost:5000", null, "test-secret", null,
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

        HttpHeaders headers = headersAfterInterceptors(config.createDasRestTemplate(noInternal));

        assertEquals("test-secret", headers.getFirst("X-API-KEY"));
        assertNull(headers.getFirst("x-internal-das-header-secret"));
    }
}
