package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What DasService actually puts on the wire, driven through the real
 * {@link Config#createDasRestTemplate} rather than a mocked RestTemplate — the mock-based
 * {@link DasServiceTest} cannot see headers contributed by the client's interceptor or by the
 * message converter that writes the body.
 */
public class DasServiceHeadersTest {

    private static final DasProperties PROPS = new DasProperties(
            "http://localhost:5000", "test-secret", "internal-secret",
            Duration.ofSeconds(5), Duration.ofSeconds(30));

    private RestTemplate template;
    private MockRestServiceServer server;
    private DasService dasService;

    @BeforeEach
    public void setUp() {
        template = new Config().createDasRestTemplate(PROPS);
        server = MockRestServiceServer.bindTo(template).build();
        dasService = new DasService(PROPS, template);
    }

    @Test
    public void testEstimateIsSentAsJsonNotXml() {
        // Jackson's XML converter also claims Map bodies and is consulted before the JSON one, so
        // without an explicit Content-Type this request goes out as application/xml and DAS breaks.
        server.expect(requestTo("http://localhost:5000/api/v1/das/data/test-uuid/estimate_size"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"uuid\":\"test-uuid\",\"output_format\":\"netcdf\"}"))
                // the per-call headers above must not displace the ones the client attaches
                .andExpect(header("X-API-KEY", "test-secret"))
                .andExpect(header("x-internal-das-header-secret", "internal-secret"))
                .andRespond(withSuccess("{\"estimated_output_bytes\":123}", MediaType.APPLICATION_JSON));

        dasService.estimateCloudOptimisedDownloadSize(
                "test-uuid", Map.of("uuid", "test-uuid", "output_format", "netcdf"));

        server.verify();
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
                "http://localhost:5000", "test-secret", null,
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        RestTemplate noInternalTemplate = new Config().createDasRestTemplate(noInternal);
        MockRestServiceServer noInternalServer = MockRestServiceServer.bindTo(noInternalTemplate).build();

        noInternalServer.expect(header("X-API-KEY", "test-secret"))
                .andExpect(headerDoesNotExist("x-internal-das-header-secret"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new DasService(noInternal, noInternalTemplate).getWaveBuoysLatestAvailableDate();

        noInternalServer.verify();
    }
}
