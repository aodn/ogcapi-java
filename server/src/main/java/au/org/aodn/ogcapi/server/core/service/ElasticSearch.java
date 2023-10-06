package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacType;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearch {
    @Value("${elasticsearch.index.name}")
    protected String indexName;

    @Autowired
    protected ElasticsearchClient esClient;

    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        Query byType = MatchQuery.of(m -> m
                .field(StacType.field)
                .query(StacType.Collection.value)
        )._toQuery();

        Query geoExist = ExistsQuery.of(m -> m
                .field(StacSummeries.Geometry.field)
        )._toQuery();

        SearchResponse<StacCollectionModel> response = esClient.search(g -> g
                .index(indexName)
                .query(q -> q.bool(b -> b.must(byType, geoExist))),
                StacCollectionModel.class);

        return response.hits()
                .hits()
                .stream()
                .map(h -> h.source())
                .collect(Collectors.toList());
    }
}
