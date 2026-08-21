package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.http.RecordingSseConnector;
import au.org.aodn.ogcapi.server.core.util.DasSseFrames;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What DasService actually puts on the wire, driven through the real clients Config builds. The
 * mock-based DasServiceTest cannot see headers added by the client itself or by the body codec.
 */
public class DasServiceHeadersTest {

    private static final DasProperties PROPS = new DasProperties(
            "http://localhost:5000", null,"test-secret", "internal-secret",
            Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

    private static final String RESULT_FRAME = """
            event: result
            data: {"status":"completed","data":{"estimated_output_bytes":123}}

            """;

    private MockRestServiceServer server;
    private RecordingSseConnector sseConnector;
    private DasService dasService;

    /**
     * The estimate goes out on the SSE client, so it is built the way Config builds it, with only
     * the transport underneath swapped for a recording one.
     */
    private static WebClient sseClientOn(DasProperties properties, RecordingSseConnector connector) {
        return new Config().createDasSseWebClient(properties, new ObjectMapper())
                .mutate()
                .clientConnector(connector)
                .build();
    }

    @BeforeEach
    public void setUp() {
        Config config = new Config();
        RestTemplate template = config.createDasRestTemplate(PROPS);
        sseConnector = new RecordingSseConnector();

        server = MockRestServiceServer.bindTo(template).build();
        dasService = new DasService(PROPS, template, sseClientOn(PROPS, sseConnector), new ObjectMapper());
    }

    @Test
    public void testEstimateIsSentAsJsonNotXml() throws Exception {
        // Jackson's XML encoder also claims Map bodies, so without an explicit Content-Type this
        // could go out as application/xml and DAS would break. It is a streamed endpoint, so we
        // accept text/event-stream and DAS replies with the payload in a terminal result frame.
        sseConnector.respondWith(List.of(RESULT_FRAME));

        dasService.estimateCloudOptimisedDownloadSize(
                "test-uuid", Map.of("uuid", "test-uuid", "output_format", "netcdf"),
                DasSseFrames.FrameCallback.IGNORE);

        assertEquals(HttpMethod.POST, sseConnector.method());
        assertEquals(URI.create("http://localhost:5000/api/v1/das/data/test-uuid/estimate_size"),
                sseConnector.uri());

        HttpHeaders headers = sseConnector.headers();
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, headers.getFirst(HttpHeaders.ACCEPT));
        // the per-call headers above must not displace the ones the client attaches
        assertEquals("test-secret", headers.getFirst("X-API-KEY"));
        assertEquals("internal-secret", headers.getFirst("x-internal-das-header-secret"));

        assertEquals(Map.of("uuid", "test-uuid", "output_format", "netcdf"),
                new ObjectMapper().readValue(sseConnector.body(), new TypeReference<Map<String, String>>() {
                }));
    }

    @Test
    public void testFeatureCollectionCarriesCredentials() {
        server.expect(requestTo("http://localhost:5000/api/v1/das/data/feature-collection/wave-buoy/latest"))
                .andExpect(header("X-API-KEY", "test-secret"))
                .andExpect(header("x-internal-das-header-secret", "internal-secret"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        dasService.getWaveBuoysLatestAvailableDate();

        server.verify();
    }

    @Test
    public void testInternalSecretIsOmittedWhenNotConfigured() {
        DasProperties noInternal = new DasProperties(
                "http://localhost:5000", null,"test-secret", null,
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));
        Config config = new Config();
        RestTemplate noInternalTemplate = config.createDasRestTemplate(noInternal);
        MockRestServiceServer noInternalServer = MockRestServiceServer.bindTo(noInternalTemplate).build();

        noInternalServer.expect(header("X-API-KEY", "test-secret"))
                .andExpect(headerDoesNotExist("x-internal-das-header-secret"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        RecordingSseConnector connector = new RecordingSseConnector().respondWith(List.of(RESULT_FRAME));
        DasService service = new DasService(
                noInternal, noInternalTemplate, sseClientOn(noInternal, connector), new ObjectMapper());

        service.getWaveBuoysLatestAvailableDate();
        noInternalServer.verify();

        // The streamed client is built from the same properties, so it must leave it off too.
        service.estimateCloudOptimisedDownloadSize(
                "test-uuid", Map.of("uuid", "test-uuid"), DasSseFrames.FrameCallback.IGNORE);
        assertNull(connector.headers().getFirst("x-internal-das-header-secret"));
    }
}
