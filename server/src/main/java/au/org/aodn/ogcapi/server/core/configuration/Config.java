package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.das.DasProperties;
import au.org.aodn.ogcapi.server.core.service.dda.DdaProperties;
import au.org.aodn.ogcapi.server.core.service.geonetwork.GNProperties;
import au.org.aodn.ogcapi.server.core.service.indexer.IndexerProperties;
import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        DdaProperties.class,
        IndexerProperties.class,
        GNProperties.class,
        DasProperties.class
})
public class Config {

    public static final String DAS_REST_TEMPLATE = "dasRestTemplate";
    public static final String DAS_SSE_REST_TEMPLATE = "dasSseRestTemplate";

    @Autowired
    ObjectMapper mapper;

    @Autowired
    public void initConstructUtils(ObjectMapper mapper) {
        ConstructUtils.setObjectMapper(mapper);
    }

    @PostConstruct
    public void init() {
        // register module for json serializing
        mapper.registerModule(new JsonNullableModule());
        // Configure ObjectMapper to exclude null fields while serializing
        mapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL,
                        JsonInclude.Include.USE_DEFAULTS
                )
        );
    }

    @Bean
    public RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1200000); // 20 minutes connection timeout
        factory.setReadTimeout(1200000);    // 20 minutes read timeout for large downloads

        return new RestTemplate(factory);
    }

    @Bean(name = DAS_REST_TEMPLATE, defaultCandidate = false)
    public RestTemplate createDasRestTemplate(DasProperties dasProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(dasProperties.connectTimeout());
        factory.setReadTimeout(dasProperties.readTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(dasCredentials(dasProperties));
        return restTemplate;
    }

    /**
     * The DAS client for streamed endpoints (the cloud-optimised size estimate). It is separate
     * from createDasRestTemplate because a stream needs two things a plain call does not:
     * 1. A longer timeout. This factory's read timeout caps the whole exchange, not each read,
     *    so it uses the generous sseReadTimeout while the shared bean keeps its short
     *    readTimeout for calls that should answer quickly.
     * 2. A cancellable body. Closing the response body cancels the exchange, and that is the
     *    only way to stop a stream: on Java 17 the reader swallows InterruptedException, so the
     *    read loop must notice for itself. See SseSession.probeClient.
     */
    @Bean(name = DAS_SSE_REST_TEMPLATE, defaultCandidate = false)
    public RestTemplate createDasSseRestTemplate(DasProperties dasProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(dasProperties.connectTimeout())
                // HttpURLConnection follows redirects on GET; the JDK client follows none by
                // default, so ask for the equivalent rather than silently changing behaviour.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(dasProperties.sseReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(dasCredentials(dasProperties));
        return restTemplate;
    }

    /**
     * Attaches the DAS credentials to every request. Lives on the client rather than on each
     * call so no caller can forget them — and so they never ride along on the shared template
     * GeoServer uses.
     */
    private ClientHttpRequestInterceptor dasCredentials(DasProperties dasProperties) {
        return (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.set("X-API-KEY", dasProperties.secret());
            if (dasProperties.internal() != null) {
                headers.set("x-internal-das-header-secret", dasProperties.internal());
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestTemplateUtils createRestTemplateUtils(RestTemplate restTemplate) {
        return new RestTemplateUtils(restTemplate);
    }

    @Bean
    public GeometryUtils createGeometryUtils() {
        return new GeometryUtils();
    }
}
