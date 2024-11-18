package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpMapperBase;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.endpoints.BinaryDataResponse;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.Request;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public class ElasticBinaryClient implements Client {

    // Create CBOR mapper
    protected CBORFactory cborFactory = new CBORFactory();
    protected ObjectMapper cborMapper = new ObjectMapper(cborFactory);

    protected RestClient client;
    protected ElasticsearchClient jsonClient;
    protected ObjectMapper jsonMapper;

    public ElasticBinaryClient(RestClient client, ElasticsearchClient elasticsearchClient, ObjectMapper objectMapper) {
        this.client = client;
        this.jsonClient = elasticsearchClient;
        this.jsonMapper = objectMapper;
    }

    @Override
    public <T> SearchResponse<T> search(SearchRequest request, Class<T> classOfT) throws IOException {

        String[] rString = request.toString().split(" ", 4);

        Map<?, ?> queryMap = jsonMapper.readValue(rString[3], Map.class);
        byte[] r = cborMapper.writeValueAsBytes(queryMap);

        Request req = new Request(rString[1], rString[2]);
        req.setEntity(
                new InputStreamEntity(new ByteArrayInputStream(r), ContentType.create("application/cbor"))
        );

        Response response = client.performRequest(req);

        Map val = cborMapper.readValue(
                response.getEntity().getContent(),
                Map.class);
        String jsonResponse = jsonMapper.writeValueAsString(val);

        // Create a deserializer for SearchResponse<MyDocument>
        JsonpDeserializer<T> deserializer = JsonpDeserializer.of(classOfT);

        // Create a JsonpMapper (Jackson-based for this example)
        JsonpMapper jsonpMapper = new JacksonJsonpMapper()
                .withAttribute("co.elastic.clients:Deserializer:_global.search.TDocument", deserializer);

        // Deserialize the binary data into a SearchResponse<MyDocument>
        return (SearchResponse<T>)(SearchResponse._DESERIALIZER.deserialize(
                jsonpMapper.jsonProvider().createParser(new StringReader(jsonResponse)),
                jsonpMapper
        ));

    }

    @Override
    public BinaryResponse searchMvt(SearchMvtRequest request) throws IOException, ElasticsearchException {
        return jsonClient.searchMvt(request);
    }
}
