package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.CacheNoLandGeometry;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.Search;
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
    @ConditionalOnMissingBean(RestClientTransport.class)
    public RestClientTransport restClientTransport() {
        // Create the low-level client
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .setCompressionEnabled(true)
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient geoNetworkElasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
    /**
     * The elastic search client to do the query
     * @param client - The elastic search client
     * @param mapper - Object mapper for string to object transformation
     * @param indexName - The elastic index name that store the STAC from es-indexer
     * @param pageSize - Do not set this value too high, say 5000 will crash elastic search
     * @param searchAsYouTypeSize - The number of search result return for search as you type
     * @return
     */
    @Bean
    public Search createElasticSearch(ElasticsearchClient client,
                                      CacheNoLandGeometry cacheNoLandGeometry,
                                      ObjectMapper mapper,
                                      @Value("${elasticsearch.index.name}") String indexName,
                                      @Value("${elasticsearch.index.pageSize:2200}") Integer pageSize,
                                      @Value("${elasticsearch.search_as_you_type.size:10}") Integer searchAsYouTypeSize) {

        return new ElasticSearch(client, cacheNoLandGeometry, mapper, indexName, pageSize, searchAsYouTypeSize);
    }
}
