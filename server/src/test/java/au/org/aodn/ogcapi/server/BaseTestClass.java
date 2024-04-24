package au.org.aodn.ogcapi.server;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

import org.json.JSONException;
import org.json.JSONObject;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.commons.io.IOUtils;

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
    protected String record_index_name;

    @Value("${elasticsearch.searchAsYouType.category_suggest.index_name}")
    protected String ardc_categories_index_name;


    @Autowired
    protected ObjectMapper indexerObjectMapper;

    protected Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    protected String getBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc";
    }

    protected String getExternalBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc/ext";
    }

    protected void clearElasticIndex() throws IOException {

        List<Map<String, String>> schemas = List.of(
                Map.of("name", record_index_name, "mapping", "aodn_discovery_parameter_vocabularies_index.json"),
                Map.of("name", ardc_categories_index_name, "mapping", "portal_records_index_schema.json")
        );

        logger.debug("Clear elastic index");
        try {
            schemas.forEach(schema -> {
                try {
                    client.deleteByQuery(f -> f
                        .index(schema.get("name"))
                        .query(QueryBuilders.matchAll().build()._toQuery())
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // Must all, otherwise index is not rebuild immediately
                try {
                    client.indices().refresh();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        catch(ElasticsearchException e) {
            // It is ok to ignore exception if the index is not found
        }
    }

    protected void createElasticIndex() {

        List<Map<String, String>> schemas = List.of(
                Map.of("name", record_index_name, "mapping", "aodn_discovery_parameter_vocabularies_index.json"),
                Map.of("name", ardc_categories_index_name, "mapping", "portal_records_index_schema.json")
        );

        schemas.forEach(schema -> {
            try {
                // TODO: This file should come from indexer jar when CodeArtifact in place
                File f = ResourceUtils.getFile(String.format("classpath:%s", schema.get("mapping")));
                try (Reader reader = new FileReader(f)) {
                    CreateIndexRequest req = CreateIndexRequest.of(b -> b
                        .index(schema.get("name"))
                        .withJson(reader)
                    );
                    client.indices().create(req);
                }
                catch(ElasticsearchException ese) {
                    // Ignore it, happens when index already created
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected void insertJsonToElasticIndex(String... filenames) throws IOException {

        // Now insert json to index
        for(String filename : filenames) {
            File j = ResourceUtils.getFile("classpath:databag/" + filename);

            try(Reader reader = new FileReader(j)) {
                IndexResponse indexResponse = client.index(i -> i
                        .index(record_index_name)
                        .withJson(reader));

                logger.info("Sample file {}, indexed with response : {}", filename, indexResponse);
            } catch (IOException e) {
                logger.error("Error indexing file {}: {}", filename, e);
            }
        }
        // Must all, otherwise index is not rebuild immediately
        client.indices().refresh();

        // Check the number of doc store inside the ES instance is correct
        SearchRequest.Builder b = new SearchRequest.Builder()
            .index(record_index_name)
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
