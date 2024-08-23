package au.org.aodn.ogcapi.server;

import au.org.aodn.ogcapi.server.core.model.ParameterVocabModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    protected String record_index_name;

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabs_index_name;

    protected Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    protected String getBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc";
    }

    protected String getExternalBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc/ext";
    }

    protected void clearElasticIndex() throws IOException {

        List<Map<String, String>> schemas = List.of(
                Map.of("name", vocabs_index_name, "mapping", "vocabs_index_schema.json"),
                Map.of("name", record_index_name, "mapping", "portal_records_index_schema.json")
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
                Map.of("name", record_index_name, "mapping", "portal_records_index_schema.json"),
                Map.of("name", vocabs_index_name, "mapping", "vocabs_index_schema.json")
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

    protected void insertTestAodnDiscoveryParameterVocabs() {
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        try {
            // Read the JSON file
            File file = ResourceUtils.getFile("classpath:databag/aodn_discovery_parameter_vocabs.json");
            // Parse the JSON content
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(file);
            // Get the array from the JSON content
            JsonNode parameter_vocabs = jsonNode.get("parameter_vocabs");
            // Iterate over the JSON array
            for (JsonNode vocab : parameter_vocabs) {
                // convert parameterVocabModel values to binary data
                logger.debug("Ingested json is {}", vocab);
                // send bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                    .index(idx -> idx
                        .index(vocabs_index_name)
                        .document(vocab)
                    )
                );
            }
            BulkResponse result = client.bulk(bulkRequest.build());
            assertEquals(parameter_vocabs.size(), result.items().size(), "Number of docs stored is correct");
        } catch (JsonProcessingException e) {
            logger.error("Failed to ingest test parameter vocabs to {}", vocabs_index_name);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
