package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.transport.endpoints.BinaryResponse;

import java.io.IOException;

public class ElasticJsonClient implements Client {

    protected ElasticsearchClient client;

    public ElasticJsonClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public <T> SearchResponse<T> search(SearchRequest request, Class<T> type) throws IOException {
        return client.search(request, type);
    }

    @Override
    public BinaryResponse searchMvt(SearchMvtRequest request) throws IOException, ElasticsearchException {
        return client.searchMvt(request);
    }
}
