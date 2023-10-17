package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.CQLToElasticFilterFactory;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search_mvt.GridType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticSearch implements Search {
    protected Logger logger = LoggerFactory.getLogger(ElasticSearch.class);

    @Value("${elasticsearch.index.name}")
    protected String indexName;

    @Autowired
    protected ElasticsearchClient esClient;

    @Autowired
    protected ObjectMapper mapper;

    protected BoolQuery createBoolQueryForProperties(List<Query> must, List<Query> should, List<Query> filters) {
        BoolQuery.Builder builder = new BoolQuery.Builder();

        if(must != null && !must.isEmpty()) {
            builder.must(must);
        }
        else {
            /*
             Equals to "must": { "match_all": {} }, that is match any, without this empty bool will fail if filter exist
             */
            builder.must(QueryBuilders.matchAll().build()._toQuery());
        }

        if(should != null && !should.isEmpty()) {
            builder.minimumShouldMatch("1");
            builder.should(should);
        }

        if(filters != null && !filters.isEmpty()) {
            builder.filter(filters);
        }
        return builder.build();
    }

    protected List<StacCollectionModel> searchCollectionBy(List<Query> queries, List<Query> should, List<Query> filters, Integer from, Integer size) throws IOException {

        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.source(f -> f.fetch(true))
                .index(indexName)
                .size(size)         // Max hit to return
                .from(from)         // Skip how many record
                .query(q -> q.bool(createBoolQueryForProperties(queries, should, filters)));

        SearchRequest request = builder.build();
        logger.debug("Final elastic search payload {}", request.toString());

        try {
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
                        } catch (JsonProcessingException e) {
                            logger.error("Failed to convert text to StacCollectionModel", e);
                            return null;
                        }
                    })
                    .filter(f -> f != null)
                    .collect(Collectors.toList());
        }
        catch(ElasticsearchException ee) {
            logger.warn("Elastic exception on query, reason is {}", ee.error().rootCause());
            throw ee;
        }
    }

    @Override
    public List<StacCollectionModel> searchCollectionWithGeometry(List<String> ids) throws IOException {

        List<FieldValue> values = ids.stream()
                .map(id -> FieldValue.of(id))
                .collect(Collectors.toList());

        List<Query> queries = List.of(
            MatchQuery.of(m -> m
                    .field(StacType.field)
                    .query(StacType.Collection.value))._toQuery(),

            ExistsQuery.of(m -> m
                .field(StacSummeries.Geometry.field))._toQuery()
        );

        List<Query> filters = List.of(
                TermsQuery.of(t -> t
                        .field(StacUUID.field)
                        .terms(s -> s.value(values)))._toQuery()
        );

        return searchCollectionBy(queries, null, filters, null, null);
    }

    @Override
    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        List<Query> queries = List.of(
                MatchQuery.of(m -> m
                        .field(StacType.field)
                        .query(StacType.Collection.value))._toQuery(),

                ExistsQuery.of(m -> m
                        .field(StacSummeries.Geometry.field))._toQuery()
        );

        return searchCollectionBy(queries, null, null, null, null);
    }

    @Override
    public List<StacCollectionModel> searchAllCollections() throws IOException {
        List<Query> queries = List.of(
                MatchQuery.of(m -> m
                        .field(StacType.field)
                        .query(StacType.Collection.value))._toQuery()
        );

        return searchCollectionBy(queries, null, null, null, null);
    }

    @Override
    public List<StacCollectionModel> searchByParameters(List<String> targets, String cql) throws IOException, CQLException {

        if((targets == null || targets.isEmpty()) && cql == null) {
            return searchAllCollections();
        }
        else {

            List<Query> queries = null;
            if(targets != null && !targets.isEmpty()) {
                queries = new ArrayList<>();

                for (String t : targets) {
                    Query q = MultiMatchQuery.of(m -> m
                            .fuzziness("AUTO")
                            //TODO: what keywords we want to search?
                            .fields(StacTitle.field, StacDescription.field)
                            .prefixLength(0)
                            .query(t))._toQuery();
                    queries.add(q);
                }
            }

            List<Query> filters = null;
            if(cql != null) {
                CQLToElasticFilterFactory<CQLCollectionsField> factory = new CQLToElasticFilterFactory<>(CQLCollectionsField.class);
                CompilerUtil.parseFilter(Language.CQL, cql, factory);
                filters = factory.getQueries();
            }

            return searchCollectionBy(null, queries, filters,  null, null);
        }
    }

    @Override
    public BinaryResponse searchCollectionVectorTile(List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol) throws IOException {

        SearchMvtRequest.Builder builder = new SearchMvtRequest.Builder();
        builder.index(indexName)
                .field(StacSummeries.Geometry.field)
                .zoom(tileMatrix)
                .x(tileRow.intValue())
                .y(tileCol.intValue())
                .exactBounds(Boolean.TRUE)
                .gridType(GridType.Grid);

        if(ids != null && !ids.isEmpty()) {
            List<FieldValue> values = ids.stream()
                    .map(id -> FieldValue.of(id))
                    .collect(Collectors.toList());

            List<Query> filters = List.of(
                    TermsQuery.of(t -> t
                            .field(StacUUID.field)
                            .terms(s -> s.value(values)))._toQuery());

            builder.query(q -> q.bool(b -> b.filter(filters)));
        }

        logger.debug("Final elastic search mvt payload {}", builder.toString());

        BinaryResponse er = esClient.searchMvt(builder.build());

        return er;
    }
}
