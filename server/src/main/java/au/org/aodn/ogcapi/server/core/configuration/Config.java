package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.http.CancelPropagatingJdkConnector;
import au.org.aodn.ogcapi.server.core.service.das.DasProperties;
import au.org.aodn.ogcapi.server.core.service.dda.DdaProperties;
import au.org.aodn.ogcapi.server.core.service.geonetwork.GNProperties;
import au.org.aodn.ogcapi.server.core.service.indexer.IndexerProperties;
import au.org.aodn.ogcapi.server.core.util.ConstructUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import au.org.aodn.ogcapi.server.processes.BatchJobProperties;
import au.org.aodn.ogcapi.server.processes.DownloadLimitProperties;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        DdaProperties.class,
        IndexerProperties.class,
        GNProperties.class,
        DasProperties.class,
        BatchJobProperties.class,
        DownloadLimitProperties.class,
        OgcApiProperties.class
})
public class Config {

    public static final String DAS_REST_TEMPLATE = "dasRestTemplate";
    public static final String DAS_SSE_WEB_CLIENT = "dasSseWebClient";

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
     * The DAS client for streamed endpoints (the cloud-optimised size estimate). A WebClient
     * rather than a RestTemplate because a stream has to be cancellable: stopping the read is
     * what abandons the estimate, and only a cancel reaches the socket. Two things to know:
     * 1. The connector is customised so a cancel really does reach the socket, see
     * CancelPropagatingJdkConnector.
     * 2. There is no timeout here. DasService caps each frame gap with sseIdleTimeout instead.
     */
    @Bean(name = DAS_SSE_WEB_CLIENT, defaultCandidate = false)
    public WebClient createDasSseWebClient(DasProperties dasProperties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(dasProperties.connectTimeout())
                // HttpURLConnection follows redirects on GET; the JDK client follows none by
                // default, so ask for the equivalent rather than silently changing behaviour.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new CancelPropagatingJdkConnector(httpClient))
                .baseUrl(dasProperties.host())
                // The default codec builds its own ObjectMapper. Pass the application's so the DAS
                // request body follows the same NON_NULL / JsonNullableModule config as everything else.
                .codecs(codecs -> codecs.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper)))
                .defaultHeader("X-API-KEY", dasProperties.secret());

        if (dasProperties.internal() != null) {
            builder.defaultHeader("x-internal-das-header-secret", dasProperties.internal());
        }
        return builder.build();
    }

    /**
     * Attaches the DAS credentials to every request.
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
