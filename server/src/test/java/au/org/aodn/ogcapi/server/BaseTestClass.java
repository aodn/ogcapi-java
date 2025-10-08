package au.org.aodn.ogcapi.server;

import au.org.aodn.ogcapi.server.core.model.VocabDto;
import au.org.aodn.ogcapi.server.core.model.VocabModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
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

    @Value("${elasticsearch.cloud_optimized_index.name}")
    protected String co_data_index_name;

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabs_index_name;

    @Value("${elasticsearch.cloud_optimized_index.name}")
    protected String data_index_name;

    protected Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    protected String getBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc";
    }

    protected String getExternalBasePath() {
        return "http://localhost:" + port + "/api/v1/ogc/ext";
    }

    protected List<Map<String, String>> schemas;

    @PostConstruct
    public void initSchemas() {
        schemas = List.of(
                Map.of("name", record_index_name, "mapping", "portal_records_index_schema.json"),
                Map.of("name", vocabs_index_name, "mapping", "vocabs_index_schema.json"),
                Map.of("name", data_index_name, "mapping", "data_index_schema.json")
        );
    }

    protected void clearElasticIndex() {

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
                log.error("Error", e);
            }
        });
    }

    protected void insertTestVocabs() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        List<VocabDto> vocabs = new ArrayList<>();

        // Read the JSON files
        try {
            // parameter vocabs
            File parameterVocabsFile = ResourceUtils.getFile("classpath:databag/aodn_discovery_parameter_vocabs.json");
            JsonNode parameterVocabNodes = objectMapper.readTree(parameterVocabsFile);
            // populate data for parameter vocabs list
            for (JsonNode parameterVocabNode : parameterVocabNodes) {
                VocabModel parameterVocabModel = objectMapper.readValue(parameterVocabNode.toString(), VocabModel.class);
                VocabDto vocab = VocabDto.builder().parameterVocabModel(parameterVocabModel).build();
                vocabs.add(vocab);
            }

            // parameter vocabs
            File platformVocabsFile = ResourceUtils.getFile("classpath:databag/aodn_platform_vocabs.json");
            JsonNode platformVocabsNodes = objectMapper.readTree(platformVocabsFile);
            // populate data for platform vocabs list
            for (JsonNode platformVocabsNode : platformVocabsNodes) {
                VocabModel platformVocabModel = objectMapper.readValue(platformVocabsNode.toString(), VocabModel.class);
                VocabDto vocab = VocabDto.builder().platformVocabModel(platformVocabModel).build();
                vocabs.add(vocab);
            }

            // parameter vocabs
            File organisationVocabsFile = ResourceUtils.getFile("classpath:databag/aodn_organisation_vocabs.json");
            JsonNode organisationVocabNodes = objectMapper.readTree(organisationVocabsFile);
            // populate data for organisation vocabs list
            for (JsonNode organisationVocabNode : organisationVocabNodes) {
                VocabModel organisationVocabModel = objectMapper.readValue(organisationVocabNode.toString(), VocabModel.class);
                VocabDto vocab = VocabDto.builder().organisationVocabModel(organisationVocabModel).build();
                vocabs.add(vocab);
            }

        } catch (IOException e) {
            logger.error("Failed to ingest test vocabs to {}", vocabs_index_name);
            throw new RuntimeException(e);
        }

        logger.info("Indexing all vocabs to {}", vocabs_index_name);
        bulkIndexVocabs(vocabs);
    }

    protected void bulkIndexVocabs(List<VocabDto> vocabs) throws IOException {
        // count portal index documents, or create index if not found from defined mapping JSON file
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (VocabDto vocab : vocabs) {
            // send bulk request to Elasticsearch
            bulkRequest.operations(op -> op
                .index(idx -> idx
                    .index(vocabs_index_name)
                    .document(vocab)
                )
            );
        }

        BulkResponse result = client.bulk(bulkRequest.build());

        // Flush after insert, otherwise you need to wait for next auto-refresh. It is
        // especially a problem with autotest, where assert happens very fast.
        client.indices().refresh();

        // Log errors, if any
        if (result.errors()) {
            logger.error("Bulk had errors");
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    logger.error("{} {}", item.error().reason(), item.error().causedBy());
                }
            }
        }
    }

    protected void insertJsonToElasticIndex(String index, String[] filenames) throws IOException {
        // Now insert json to index
        for(String filename : filenames) {
            File j = ResourceUtils.getFile("classpath:databag/" + filename);

            try(Reader reader = new FileReader(j)) {
                IndexResponse indexResponse = client.index(i -> i
                        .index(index)
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
                .index(index)
                .query(QueryBuilders.matchAll().build()._toQuery());

        SearchRequest request = b.build();
        logger.debug("Elastic search payload for verification {}", request.toString());

        SearchResponse<ObjectNode> response = client.search(request, ObjectNode.class);
        logger.debug(response.toString());

        assertEquals(filenames.length, response.hits().hits().size(), "Number of docs stored is correct");
        for (Hit<ObjectNode> hit : response.hits().hits()) {
            if(hit.source() != null) {
                logger.debug("Stored the following id {}", hit.source().get("id"));
            }
        }
    }

    protected void insertJsonToElasticRecordIndex(String... filenames) throws IOException {
        this.insertJsonToElasticIndex(record_index_name, filenames);
    }

    protected void insertJsonToElasticCODataIndex(String... filenames) throws IOException {
        this.insertJsonToElasticIndex(co_data_index_name, filenames);
    }

    protected Response getClusterHealth() throws IOException {
        return transport.restClient().performRequest(new Request("GET", "/_cluster/health"));
    }

    protected void assertClusterHealthResponse() throws IOException {
        Response response = getClusterHealth();
        assertEquals(200, response.getStatusLine().getStatusCode(), "Elastic 200 response");
    }

    public static String readResourceFile(String path) throws IOException {
        File f = ResourceUtils.getFile(path);
        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
    }
}
