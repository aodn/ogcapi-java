package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ExplainRequest;
import co.elastic.clients.elasticsearch.core.ExplainResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.Explanation;
import co.elastic.clients.elasticsearch.core.explain.ExplanationDetail;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticSearchBaseTest {

    @Test
    void explainCollectionByPreservesSizeAndReturnsExplanation() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        ElasticsearchTransport transport = mock(ElasticsearchTransport.class);
        when(client._transport()).thenReturn(transport);
        when(transport.jsonpMapper()).thenReturn(new JacksonJsonpMapper(objectMapper));

        Explanation explanation = Explanation.of(e -> e
                .value(1.5f)
                .description("test explanation")
                .details(List.of()));
        Hit<ObjectNode> hit = Hit.of(h -> h
                .index("test-index")
                .id("collection-id")
                .score(1.5)
                .explanation(explanation));
        SearchResponse<ObjectNode> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(1).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq))
                        .hits(hit)));
        when(client.search(any(SearchRequest.class), eq(ObjectNode.class))).thenReturn(response);

        TestElasticSearchBase elasticSearch = new TestElasticSearchBase(client, objectMapper);
        JsonNode result = elasticSearch.explain(() -> new SearchRequest.Builder()
                .index("test-index")
                .size(11)
                .query(q -> q.matchAll(m -> m)));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(requestCaptor.capture(), eq(ObjectNode.class));
        SearchRequest request = requestCaptor.getValue();

        assertEquals(11, request.size());
        assertTrue(request.explain());
        assertTrue(request.trackTotalHits().isEnabled());
        assertTrue(request.trackTotalHits().enabled());
        assertNotNull(request.source());
        assertFalse(request.source().fetch());

        assertEquals(11, result.path("request").path("size").asInt());
        assertEquals(1, result.path("total").path("value").asLong());
        assertEquals("eq", result.path("total").path("relation").asText());
        assertEquals("collection-id", result.path("hits").path(0).path("id").asText());
        assertEquals(1.5, result.path("hits").path(0).path("score").asDouble());
        assertEquals("test explanation", result.path("hits").path(0)
                .path("explanation").path("description").asText());
    }

    @Test
    void explainCollectionByIdUsesExplainApiWithBuiltQuery() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        ElasticsearchTransport transport = mock(ElasticsearchTransport.class);
        when(client._transport()).thenReturn(transport);
        when(transport.jsonpMapper()).thenReturn(new JacksonJsonpMapper(objectMapper));

        ExplanationDetail explanation = ExplanationDetail.of(e -> e
                .value(2.5f)
                .description("id explanation")
                .details(List.of()));
        ExplainResponse<ObjectNode> response = ExplainResponse.of(r -> r
                .index("test-index")
                .id("collection-id")
                .matched(true)
                .explanation(explanation));
        when(client.explain(any(ExplainRequest.class), eq(ObjectNode.class))).thenReturn(response);

        TestElasticSearchBase elasticSearch = new TestElasticSearchBase(client, objectMapper);
        elasticSearch.setIndexName("test-index");
        JsonNode result = elasticSearch.explainById("collection-id", () -> new SearchRequest.Builder()
                .index("test-index")
                .query(q -> q.matchAll(m -> m)));

        ArgumentCaptor<ExplainRequest> requestCaptor = ArgumentCaptor.forClass(ExplainRequest.class);
        verify(client).explain(requestCaptor.capture(), eq(ObjectNode.class));
        ExplainRequest request = requestCaptor.getValue();

        assertEquals("test-index", request.index());
        assertEquals("collection-id", request.id());
        assertTrue(request.query().isMatchAll());
        assertNotNull(request.source());
        assertFalse(request.source().fetch());

        assertEquals("collection-id", result.path("request").path("id").asText());
        assertTrue(result.path("request").path("query").has("match_all"));
        assertEquals("collection-id", result.path("response").path("_id").asText());
        assertTrue(result.path("response").path("matched").asBoolean());
        assertEquals("id explanation", result.path("response")
                .path("explanation").path("description").asText());
    }

    private static class TestElasticSearchBase extends ElasticSearchBase {
        private TestElasticSearchBase(ElasticsearchClient client, ObjectMapper objectMapper) {
            setEsClient(client);
            setMapper(objectMapper);
        }

        private JsonNode explain(Supplier<SearchRequest.Builder> requestSupplier) throws IOException {
            return explainCollectionBy(requestSupplier);
        }

        private JsonNode explainById(String id, Supplier<SearchRequest.Builder> requestSupplier) throws IOException {
            return explainCollectionById(id, requestSupplier);
        }
    }
}
