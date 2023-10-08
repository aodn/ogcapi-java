package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacType;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacUUID;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearch {
    @Value("${elasticsearch.index.name}")
    protected String indexName;

    @Autowired
    protected ElasticsearchClient esClient;

    protected List<StacCollectionModel> searchCollectionBy(String id, Boolean isNeededGeometry) throws IOException {
        List<Query> queries = new ArrayList<>();

        Query byType = MatchQuery.of(m -> m
                .field(StacType.field)
                .query(StacType.Collection.value))._toQuery();
        queries.add(byType);

        if(isNeededGeometry) {
            Query geoExist = ExistsQuery.of(m -> m
                    .field(StacSummeries.Geometry.field))._toQuery();
            queries.add(geoExist);
        }

        if(id != null) {
            Query uuid = MatchQuery.of(m -> m
                    .field(StacUUID.field)
                    .query(id))._toQuery();

            queries.add(uuid);
        }

        SearchResponse<StacCollectionModel> response = esClient.search(g -> g
                        .index(indexName)
                        .query(q -> q.bool(b -> b.must(queries))),
                StacCollectionModel.class);

        return response.hits()
                .hits()
                .stream()
                .map(Hit::source)
                .collect(Collectors.toList());
    }

    public List<StacCollectionModel> searchCollectionWithGeometry(String id) throws IOException {
        return searchCollectionBy(id, true);
    }
    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        return searchCollectionWithGeometry(null);
    }
    public List<StacCollectionModel> searchAllCollections() throws IOException {
        return searchCollectionBy(null, false);
    }
}
