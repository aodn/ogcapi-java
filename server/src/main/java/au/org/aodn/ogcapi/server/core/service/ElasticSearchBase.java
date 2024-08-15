package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLElasticSetting;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacBasicField;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class contains the core function to do the search, it includes some common item like ordering etc,
 * We want to keep it in one location so that we do not get distracted by other construction.
 */
@Getter
@Setter
@Slf4j
abstract class ElasticSearchBase {

    protected Integer searchAsYouTypeSize;
    protected String indexName;
    protected Integer pageSize;
    protected ElasticsearchClient esClient;
    protected ObjectMapper mapper;

    /**
     * Construct the skeleton of in the elastic query and fill in values
     * @param must - The must portion of Elastic query
     * @param should - The should portion of Elastic query
     * @param filters - The filter to the Elastic query
     * @return
     */
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

    protected SearchRequest buildSearchAsYouTypeRequest(
            List<String> destinationFields,
            String indexName,
            List<Query> searchAsYouTypeQueries,
            List<Query> filters) {

        // By default it is limited to 10 even not specify, we want to use a variable so that we can change it later if needed.
        return new SearchRequest.Builder()
                .size(searchAsYouTypeSize)
                .index(indexName)
                .source(SourceConfig.of(sc -> sc.filter(f -> f.includes(destinationFields))))
                .query(b -> b.bool(createBoolQueryForProperties(searchAsYouTypeQueries, null, filters)))
                .sort(so -> so.score(v -> v.order(SortOrder.Desc)))
                .build();
    }
    /**
     * Core function to do the search, it used pageableSearch to make sure all records are return.
     *
     * @param queries
     * @param should
     * @param filters - The Query coming from CQL parser
     * @param properties - The fields you want to return in the search, you can search a field but not include in the return
     * @param maxSize: Max number of records to be return
     * @return - The search result from Elastic query and format in StacCollectionModel
     * @throws IOException
     */
    protected List<StacCollectionModel> searchCollectionBy(final Map<CQLElasticSetting, String> querySetting,
                                                           final List<Query> queries,
                                                           final List<Query> should,
                                                           final List<Query> filters,
                                                           final List<String> properties,
                                                           final List<SortOptions> sortOptions,
                                                           final Long maxSize) {

        Supplier<SearchRequest.Builder> builderSupplier = () -> {
            SearchRequest.Builder builder = new SearchRequest.Builder();
            builder.index(indexName)
                    .size(pageSize)
                    .query(q -> q.bool(createBoolQueryForProperties(queries, should, filters)));

            if(sortOptions != null) {
                builder.sort(sortOptions);
            }

            builder.sort(so -> so
                    // We need a unique key for the search, cannot use _id in v8 anymore, so we need
                    // to sort using the keyword, this field is not for search and therefore not in enum
                    .field(FieldSort.of(f -> f
                            .field(StacBasicField.UUID.sortField)
                            .order(SortOrder.Asc))));

            if(querySetting.get(CQLElasticSetting.score) != null) {
                // By default we do not setup any min_score, the api caller should pass it in so
                // that the result is more relevant, min may be 2 seems ok
                if((queries == null || queries.isEmpty())
                        && (should == null || should.isEmpty())) {

                    // Special case if you are not doing any query then there will be no meaningful score, so
                    // setting value other than 0 makes no sense, this also applies to fuzzy field search in cql with
                    // parameter, because it falls inside the "filter" block of elastic search
                    builder.minScore(0.0);
                }
                else {
                    // The parser, after parse the score parameter, will setup the score value.
                    builder.minScore(Double.valueOf(querySetting.get(CQLElasticSetting.score)));
                }
            }

            if(properties != null && !properties.isEmpty()) {

                // Validate all properties value.
                List<String> invalid = CQLFields.findInvalidEnum(properties);

                if(invalid.isEmpty()) {
                    // Convert the income field name to the real field name in STAC
                    List<String> fs = properties
                            .stream()
                            .flatMap(v -> CQLFields.valueOf(v).getDisplayField().stream())
                            .filter(Objects::nonNull)
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
                    throw new IllegalArgumentException(String.format("Invalid properties in query %s, check ?properties=xx", invalid));
                }
            }
            else {
                builder.source(f -> f.fetch(true));
            }
            return builder;
        };

        try {
            Iterable<ObjectNode> response = pagableSearch(builderSupplier, ObjectNode.class, maxSize);
            List<StacCollectionModel> result = new ArrayList<>();

            response.forEach(
                    i -> {
                        if(i != null) {
                            result.add(this.formatResult(i));
                        }
                    });

            return result;
        }
        catch(ElasticsearchException ee) {
            log.warn("Elastic exception on query, reason is {}", ee.error().rootCause());
            throw ee;
        }
    }
    /**
     * There is a limit of how many record a query can return, this mean the record return may not be full set, you
     * need to keep loading until you reach the end of records
     *
     * @param requestBuilder, assume it is sorted with order, what order isn't important, as long as it is sorted
     * @param clazz
     * @return
     * @param <T>
     */
    protected <T> Iterable<T> pagableSearch(Supplier<SearchRequest.Builder> requestBuilder, Class<T> clazz, Long maxSize) {
        try {
            SearchRequest sr = requestBuilder.get().build();
            log.debug("Final elastic search payload {}", sr.toString());

            final AtomicLong count = new AtomicLong(0);
            final AtomicReference<SearchResponse<T>> response = new AtomicReference<>(
                    esClient.search(sr, clazz)
            );

            return () -> new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    // If we hit the end, that means we have iterated to end of page.
                    if (index < response.get().hits().hits().size()) {
                        if(maxSize != null) {
                            return count.get() < maxSize;
                        }
                        else {
                            return true;
                        }
                    }
                    else {
                        // If last index is zero that mean nothing found already, so no need to look more
                        if (index == 0) return false;

                        // Load next batch
                        try {
                            // Get the last sorted value from the last batch
                            List<FieldValue> sortedValues = response.get().hits().hits().get(index - 1).sort();

                            // Use the last builder and append the searchAfter values
                            SearchRequest request = requestBuilder.get().searchAfter(sortedValues).build();
                            log.debug("Final elastic search payload {}", request.toString());

                            response.set(esClient.search(request, clazz));
                            // Reset counter from start
                            index = 0;
                            return index < response.get().hits().hits().size();
                        }
                        catch(IOException ieo) {
                            throw new RuntimeException(ieo);
                        }
                    }
                }

                @Override
                public T next() {
                    count.incrementAndGet();

                    Hit<T> hit = response.get().hits().hits().get(index++);
                    log.info("id {}, score {}", hit.id(), hit.score());

                    return hit.source();
                }
            };
        }
        catch(IOException e) {
            log.error("Fail to fetch record", e);
        }
        return Collections.emptySet();
    }

    protected StacCollectionModel formatResult(ObjectNode nodes) {
        try {
            if(nodes != null) {
                String json = nodes.toPrettyString();
                log.debug("Serialize STAC json to StacCollectionModel {}", json);

                return mapper.readValue(json, StacCollectionModel.class);
            }
            else {
                log.error("Failed to serialize text to StacCollectionModel");
                return null;
            }
        }
        catch (JsonProcessingException e) {
            log.error("Exception failed to convert text to StacCollectionModel", e);
            return null;
        }
    }
}
