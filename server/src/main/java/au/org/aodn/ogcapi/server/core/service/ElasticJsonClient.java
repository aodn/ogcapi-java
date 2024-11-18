package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.ZonedDateTime;

@Slf4j
public class ElasticJsonClient implements Client {

    protected ElasticsearchClient client;

    public ElasticJsonClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public <T> SearchResponse<T> search(SearchRequest request, Class<T> type) throws IOException {
        log.info("Start load {}", ZonedDateTime.now());
        SearchResponse<T> v = client.search(request, type);
        log.info("End load {}", ZonedDateTime.now());

        return v;
    }

    @Override
    public BinaryResponse searchMvt(SearchMvtRequest request) throws IOException, ElasticsearchException {
        return client.searchMvt(request);
    }
}
