package au.org.aodn.ogcapi.server.core.service;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.transport.endpoints.BinaryResponse;

import java.io.IOException;

public interface Client {
    <T> SearchResponse<T> search(SearchRequest request, Class<T> type) throws IOException;
    BinaryResponse searchMvt(SearchMvtRequest request) throws IOException, ElasticsearchException;
}
