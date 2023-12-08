package au.org.aodn.ogcapi.server.core.configuration;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * We use test container with docker image throughout the testing.
 */
@Configuration
public class ElasticSearchTestConfig {

    @Lazy
    @Autowired
    protected ElasticsearchContainer container;

    public static final String ES_USERNAME = "elastic";

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

        // Create the transport with a Jackson mapper
        return new RestClientTransport(client, new JacksonJsonpMapper());
    }
}
