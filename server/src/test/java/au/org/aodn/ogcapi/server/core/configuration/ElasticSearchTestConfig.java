package au.org.aodn.ogcapi.server.core.configuration;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * We use test container with docker image throughout the testing.
 */
@Configuration
public class ElasticSearchTestConfig {

    @Lazy
    @Autowired
    protected ElasticsearchContainer container;

    @PreDestroy
    public void clear() {
        container.close();
    }

    @Bean("ES_PASSWORD")
    public String generateRandomPasswordForElasticContainer() {
        return UUID.randomUUID().toString();
    }

    @Bean
    public ElasticsearchContainer createElasticDockerTestContainer(
            @Qualifier("ES_PASSWORD") String password,
            @Value("${ogcapi.docker.elasticVersion}") String version) throws InterruptedException {

        final DockerImageName ELASTICSEARCH_IMAGE = DockerImageName
                .parse("docker.elastic.co/elasticsearch/elasticsearch")
                .withTag(version);

        ElasticsearchContainer container = new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
                .withPassword(password);

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        container.start();

        new Thread(() -> {
            while(!container.isRunning()) {
                try {
                    countDownLatch.await(1, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    // Do nothing
                }
            }
            countDownLatch.countDown();
        }).start();

        // Wait till creation down before return
        countDownLatch.await();
        return container;
    }
    /**
     * Superseded the rest client transport in the run, so test case use this test container.
     * @param password
     * @return
     */
    @Bean
    public RestClientTransport testRestClientTransport(@Qualifier("ES_PASSWORD") String password) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", password)
        );

        RestClient client = RestClient
                        .builder(HttpHost.create(container.getHttpHostAddress()))
                        .setHttpClientConfigCallback(httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                        )
                        .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(client, new JacksonJsonpMapper());
    }
}
