package au.org.aodn.ogcapi.server.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.util.ResourceUtils;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseTestClass {

    @LocalServerPort
    private int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected RestClientTransport transport;

    @Autowired
    protected ElasticsearchClient client;

    @Autowired
    protected ElasticsearchContainer container;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    protected Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    protected String getBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc";
    }

    protected String getExternalBasePath() {
        return "http://localhost:" + port + "/api/v1/ogcapi/ext";
    }

    protected void clearElasticIndex() throws IOException {
        logger.debug("Clear elastic index");
        try {
            client.deleteByQuery(f -> f
                    .index(INDEX_NAME)
                    .query(QueryBuilders.matchAll().build()._toQuery())
            );
            // Must all, otherwise index is not rebuild immediately
            client.indices().refresh();
        }
        catch(ElasticsearchException e) {
            // It is ok to ignore exception if the index is not found
        }
    }

    protected void createElasticIndex() throws IOException {
        // TODO: This file should come from indexer jar when CodeArtifact in place
        File f = ResourceUtils.getFile("classpath:portal_records_index_schema.json");
        try (Reader reader = new FileReader(f)) {
            CreateIndexRequest req = CreateIndexRequest.of(b -> b
                    .index(INDEX_NAME)
                    .withJson(reader)
            );
            client.indices().create(req);
        }
        catch(ElasticsearchException ese) {
            // Ignore it, happens when index already created
        }
    }

    protected void insertJsonToElasticIndex(String... filenames) throws IOException {

        // Now insert json to index
        for(String filename : filenames) {
            File j = ResourceUtils.getFile("classpath:databag/" + filename);

            try(Reader reader = new FileReader(j)) {
                IndexResponse indexResponse = client.index(i -> i
                        .index(INDEX_NAME)
                        .withJson(reader));

                logger.info("Sample file {}, indexed with response : {}", filename, indexResponse);
            }
        }
        // Must all, otherwise index is not rebuild immediately
        client.indices().refresh();

        // Check the number of doc store inside the ES instance is correct
        SearchRequest.Builder b = new SearchRequest.Builder()
                .index(INDEX_NAME)
                        .query(QueryBuilders.matchAll().build()._toQuery());

        SearchRequest request = b.build();
        logger.debug("Elastic search payload for verification {}", request.toString());

        SearchResponse<ObjectNode> response = client.search(request, ObjectNode.class);
        logger.debug(response.toString());

        assertEquals(filenames.length, response.hits().hits().size(), "Number of docs stored is correct");
    }

    protected Response getClusterHealth() throws IOException {
        return transport.restClient().performRequest(new Request("GET", "/_cluster/health"));
    }

    protected void assertClusterHealthResponse() throws IOException {
        Response response = getClusterHealth();
        assertEquals(200, response.getStatusLine().getStatusCode(), "Elastic 200 response");
    }
}
