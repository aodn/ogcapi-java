package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.*;
import au.org.aodn.ogcapi.server.core.parser.CQLToElasticFilterFactory;
import au.org.aodn.ogcapi.server.core.parser.ElasticFilter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchMvtRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search_mvt.GridType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
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

    protected ElasticsearchClient esClient;

    @Autowired
    protected ObjectMapper mapper;

    public ElasticSearch(ElasticsearchClient client) {
        this.esClient = client;
    }

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

    protected List<StacCollectionModel> searchCollectionBy(List<Query> queries,
                                                           List<Query> should,
                                                           List<Query> filters,
                                                           List<String> property,
                                                           Integer from,
                                                           Integer size) throws IOException {

        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(indexName)
                .size(size)         // Max hit to return
                .from(from)         // Skip how many record
                .query(q -> q.bool(createBoolQueryForProperties(queries, should, filters)));

        if(property != null) {
            // Convert the income field name to the real field name in STAC
            List<String> fs = property
                    .stream()
                    .map(v -> CQLCollectionsField.valueOf(v).getDisplayField())
                    .collect(Collectors.toList());

            // Set fetch to false so that it do not return the original document but just the field
            // we set
            builder.source(f -> f
                    .filter(sf -> sf
                            .includes(fs)
                    )
            );
        }
        else {
            builder.source(f -> f.fetch(true));
        }

        SearchRequest request = builder.build();
        logger.debug("Final elastic search payload {}", request.toString());

        try {
            SearchResponse<ObjectNode> response = esClient.search(request, ObjectNode.class);

            return response
                    .hits()
                    .hits()
                    .stream()
                    .map(m -> formatResult(m))
                    .filter(f -> f != null)
                    .collect(Collectors.toList());
        }
        catch(ElasticsearchException ee) {
            logger.warn("Elastic exception on query, reason is {}", ee.error().rootCause());
            throw ee;
        }
    }

    protected StacCollectionModel formatResult(Hit<ObjectNode> nodes) {
        try {
            if(nodes.source() != null) {
                String json = nodes.source().toPrettyString();
                logger.debug("Converted json to StacCollectionModel {}", json);

                return mapper.readValue(json, StacCollectionModel.class);
            }
            else {
                logger.error("Failed to convert text to StacCollectionModel");
                return null;
            }
        }
        catch (JsonProcessingException e) {
            logger.error("Exception failed to convert text to StacCollectionModel", e);
            return null;
        }
    }

    protected List<StacCollectionModel> searchCollectionsByIds(List<String> ids, Boolean isWithGeometry) throws IOException {

        List<Query> queries = List.of(MatchQuery.of(m -> m
                            .field(StacType.searchField)
                            .query(StacType.Collection.value))._toQuery());

        if(isWithGeometry) {
            queries.add(ExistsQuery.of(m -> m
                    .field(StacSummeries.Geometry.searchField))._toQuery());
        }

        List<Query> filters = null;
        if(ids != null && !ids.isEmpty()) {
            List<FieldValue> values = ids.stream()
                    .map(id -> FieldValue.of(id))
                    .collect(Collectors.toList());

            filters = List.of(
                    TermsQuery.of(t -> t
                            .field(StacUUID.UUID.searchField)
                            .terms(s -> s.value(values)))._toQuery()
            );
        }

        return searchCollectionBy(queries, null, filters, null, null, null);
    }

    @Override
    public List<StacCollectionModel> searchCollectionWithGeometry(List<String> ids) throws Exception {
        return searchCollectionsByIds(ids, Boolean.TRUE);
    }

    @Override
    public List<StacCollectionModel> searchAllCollectionsWithGeometry() throws IOException {
        return searchCollectionsByIds(null, Boolean.TRUE);
    }

    @Override
    public List<StacCollectionModel> searchCollections(List<String> ids) throws Exception {
        return searchCollectionsByIds(ids, Boolean.FALSE);
    }

    @Override
    public List<StacCollectionModel> searchAllCollections() throws IOException {
        return searchCollectionsByIds(null, Boolean.FALSE);
    }

    @Override
    public List<StacCollectionModel> searchByParameters(List<String> keywords, String cql, CQLCrsType coor, List<String> property) throws IOException, CQLException {

        if((keywords == null || keywords.isEmpty()) && cql == null) {
            return searchAllCollections();
        }
        else {

            List<Query> queries = null;
            if(keywords != null && !keywords.isEmpty()) {
                queries = new ArrayList<>();

                for (String t : keywords) {
                    Query q = MultiMatchQuery.of(m -> m
                            .fuzziness("AUTO")
                            //TODO: what keywords we want to search?
                            .fields(StacTitle.searchField, StacDescription.searchField)
                            .prefixLength(0)
                            .query(t))._toQuery();
                    queries.add(q);
                }
            }

            List<Query> filters = null;
            if(cql != null) {
                CQLToElasticFilterFactory<CQLCollectionsField> factory = new CQLToElasticFilterFactory<>(coor, CQLCollectionsField.class);
                Filter filter = CompilerUtil.parseFilter(Language.CQL, cql, factory);

                if(filter instanceof ElasticFilter elasticFilter) {
                    filters = List.of(elasticFilter.getQuery());
                }
            }

            return searchCollectionBy(null, queries, filters,  property, null, null);
        }
    }

    @Override
    public BinaryResponse searchCollectionVectorTile(List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol) throws IOException {

        SearchMvtRequest.Builder builder = new SearchMvtRequest.Builder();
        builder.index(indexName)
                .field(StacSummeries.Geometry.searchField)
                .zoom(tileMatrix)
                .x(tileRow.intValue())
                .y(tileCol.intValue())
                // If true, the meta layer’s feature is a bounding box resulting from a geo_bounds aggregation.
                // The aggregation runs on <field> values that intersect the <zoom>/<x>/<y> tile with wrap_longitude
                // set to false. The resulting bounding box may be larger than the vector tile.
                .exactBounds(Boolean.FALSE)
                .gridType(GridType.Grid);

        if(ids != null && !ids.isEmpty()) {
            List<FieldValue> values = ids.stream()
                    .map(id -> FieldValue.of(id))
                    .collect(Collectors.toList());

            List<Query> filters = List.of(
                    TermsQuery.of(t -> t
                            .field(StacUUID.UUID.searchField)
                            .terms(s -> s.value(values)))._toQuery());

            builder.query(q -> q.bool(b -> b.filter(filters)));
        }

        logger.debug("Final elastic search mvt payload {}", builder.toString());

        BinaryResponse er = esClient.searchMvt(builder.build());

        return er;
    }
}
