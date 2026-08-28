package au.org.aodn.ogcapi.server.core.configuration;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/**
 * We use test container with docker image throughout the testing.
 */
@Configuration
public class ElasticSearchTestConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchTestConfig.class);

    @Lazy
    @Autowired
    protected ElasticsearchContainer container;

    public static final String ES_USERNAME = "elastic";

    @PreDestroy
    public void shutDownElasticSearch() {
        container.close();
    }

    @Bean
    public ElasticsearchContainer createElasticDockerTestContainer(
            @Value("${ogcapi.docker.elasticVersion}") String version) {

        final DockerImageName ELASTICSEARCH_IMAGE = DockerImageName
                .parse("docker.elastic.co/elasticsearch/elasticsearch")
                .withTag(version);

        final HttpWaitStrategy httpsWaitStrategy = Wait
                .forHttps("/")
                .forPort(9200)
                .forStatusCode(200)
                .withBasicCredentials(ES_USERNAME, ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD)
                // trusting self-signed certificate
                .allowInsecure();

        ElasticsearchContainer container = new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
                .withEnv("xpack.license.self_generated.type", "trial")
                .waitingFor(httpsWaitStrategy);

        container.start();
        return container;
    }
    /**
     * Superseded the rest client transport in the run, so test case use this test container.
     * @return
     */
    @Bean
    public RestClientTransport testRestClientTransport() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(
                        ElasticSearchTestConfig.ES_USERNAME,
                        ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD
                )
        );

        RestClient client = RestClient
                .builder(HttpHost.create("https://" + container.getHttpHostAddress()))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    httpClientBuilder.setSSLContext(container.createSslContextFromCa());
                    return httpClientBuilder;
                })
                .build();

        startTrialLicense(client);

        // Create the transport with a Jackson mapper
        return new RestClientTransport(client, new JacksonJsonpMapper());
    }

    /**
     * Testcontainers ships a basic licence. {@code semantic_text} needs the {@code inference}
     * feature, which a 30-day self-generated trial enables. The container is discarded after tests.
     */
    private static void startTrialLicense(RestClient client) {
        try {
            Request request = new Request("POST", "/_license/start_trial");
            request.addParameter("acknowledge", "true");
            Response response = client.performRequest(request);
            log.info("Elasticsearch trial licence start returned {}", response.getStatusLine());
        } catch (IOException e) {
            log.warn("Could not start Elasticsearch trial licence (may already be trial): {}", e.getMessage());
        }
    }
}
