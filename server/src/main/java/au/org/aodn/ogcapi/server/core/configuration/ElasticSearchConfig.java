package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * If user provide another search engine, this one will not be created .
 */
@Configuration
@ConditionalOnMissingBean(Search.class)
public class ElasticSearchConfig {

    @Value("${elasticsearch.serverUrl}")
    private String serverUrl;

    @Value("${elasticsearch.apiKey}")
    private String apiKey;

    @Bean
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient createRestClient() {
        // Create the low-level client
        return RestClient
                .builder(HttpHost.create(serverUrl))
                .setCompressionEnabled(true)
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();
    }

    @Bean
    public RestClientTransport restClientTransport(RestClient restClient) {
        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient geoNetworkElasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }

//    @Bean
//    public Client createElasticBinaryClient(RestClient restClient, ElasticsearchClient elasticsearchClient, ObjectMapper objectMapper) {
//        return new ElasticBinaryClient(restClient, elasticsearchClient, objectMapper);
//    }

    @Bean
    public Client createElasticJsonClient(ElasticsearchClient elasticsearchClient) {
        return new ElasticJsonClient(elasticsearchClient);
    }

    @Bean
    public Search createElasticSearch(Client client,
                                      ObjectMapper mapper,
                                      @Value("${elasticsearch.index.name}") String indexName,
                                      @Value("${elasticsearch.index.pageSize:2000}") Integer pageSize,
                                      @Value("${elasticsearch.search_as_you_type.size:10}") Integer searchAsYouTypeSize) {

        return new ElasticSearch(client, mapper, indexName, pageSize, searchAsYouTypeSize);
    }
}
