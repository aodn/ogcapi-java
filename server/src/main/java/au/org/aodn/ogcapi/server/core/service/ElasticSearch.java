package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearch {
    protected Logger logger = LoggerFactory.getLogger(ElasticSearch.class);

    @Value("${elasticsearch.index.name}")
    protected String indexName;

    @Autowired
    protected ElasticsearchClient esClient;

    @Autowired
    protected ObjectMapper mapper;

    protected List<StacCollectionModel> searchCollectionBy(List<Query> queries, Boolean isShouldOperation) throws IOException {

        SearchRequest request = SearchRequest.of(g -> g
//                .size(10000)
                .index(indexName)
                .source(f -> f.fetch(true))
                .query(q -> q.bool(b -> {
                    b.minimumShouldMatch("1");
                    if(isShouldOperation) b.should(queries); else b.must(queries);
                    return b;
                })));

        logger.info(request.source().toString());

        SearchResponse<ObjectNode> response = esClient.search(request, ObjectNode.class);

        return response
                .hits()
                .hits()
                .stream()
                .map(m -> {
                    String json = m.source().toPrettyString();
                    logger.debug("Concert json to StacCollectionModel {}", json);
                    try {
                        return mapper.readValue(json, StacCollectionModel.class);
                    }
                    catch (JsonProcessingException e) {
                        logger.error("Failed to convert text to StacCollectionModel", e);
                        return null;
                    }
                })
                .filter(f -> f != null)
                .collect(Collectors.toList());
    }

    public List<StacCollectionModel> searchCollectionWithGeometry(String id) throws IOException {
        List<Query> queries = List.of(
            MatchQuery.of(m -> m
                    .field(StacType.field)
                    .query(StacType.Collection.value))._toQuery(),

            ExistsQuery.of(m -> m
                .field(StacSummeries.Geometry.field))._toQuery(),

            MatchQuery.of(m -> m
                .field(StacUUID.field)
                .query(id))._toQuery()
        );

        return searchCollectionBy(queries, Boolean.FALSE);
    }

    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        List<Query> queries = List.of(
                MatchQuery.of(m -> m
                        .field(StacType.field)
                        .query(StacType.Collection.value))._toQuery(),

                ExistsQuery.of(m -> m
                        .field(StacSummeries.Geometry.field))._toQuery()
        );

        return searchCollectionBy(queries, Boolean.FALSE);
    }

    public List<StacCollectionModel> searchAllCollections() throws IOException {
        List<Query> queries = List.of(
                MatchQuery.of(m -> m
                        .field(StacType.field)
                        .query(StacType.Collection.value))._toQuery()
        );

        return searchCollectionBy(queries, Boolean.FALSE);
    }

    public List<StacCollectionModel> searchByTitleDescKeywords(List<String> targets) throws IOException {

        if(targets == null || targets.isEmpty()) {
            return searchAllCollections();
        }
        else {
            List<Query> queries = new ArrayList<>();

            for (String t : targets) {
                Query q = MatchQuery.of(m -> m
                        .fuzziness("AUTO")
                        .field(StacTitle.field)
                        .prefixLength(0)
                        .query(t))._toQuery();
                queries.add(q);

                q = MatchQuery.of(m -> m
                        .fuzziness("AUTO")
                        .field(StacDescription.field)
                        .prefixLength(0)
                        .query(t))._toQuery();
                queries.add(q);

                //TODO: what keywords we want to search?
            }

            return searchCollectionBy(queries, Boolean.TRUE);
        }
    }
}
