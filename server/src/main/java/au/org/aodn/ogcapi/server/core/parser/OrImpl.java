package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Or;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrImpl extends QueryHandler implements Or {

    protected List<Filter> children = new ArrayList<>();

    public OrImpl(Filter filter1, Filter filter2) {

        if(filter1 instanceof ElasticSetting && filter2 instanceof QueryHandler elasticFilter2) {
            this.addErrors(elasticFilter2.getErrors());
            throw new IllegalArgumentException("Or combine with query setting do not make sense");
        }
        else if(filter2 instanceof ElasticSetting && filter1 instanceof QueryHandler elasticFilter1){
            this.addErrors(elasticFilter1.getErrors());
            throw new IllegalArgumentException("Or combine with query setting do not make sense");
        }
        else if(filter1 instanceof QueryHandler elasticFilter1 && filter2 instanceof QueryHandler elasticFilter2) {
            // If the CQL contains ElasticSetting then the query will be null, this check is used to make sure
            // we ignore those null query
            if(elasticFilter1.query != null && elasticFilter2.query != null) {
                this.query = BoolQuery.of(f -> f
                        .should(elasticFilter1.query, elasticFilter2.query)
                )._toQuery();
            }
            else if(elasticFilter1.query != null) {
                this.query = elasticFilter1.query;
            }
            else {
                this.query = elasticFilter2.query;
            }

            children.add(filter1);
            children.add(filter2);

            // Remember to copy the error from child
            this.addErrors(elasticFilter1.getErrors());
            this.addErrors(elasticFilter2.getErrors());
        }
    }

    public OrImpl(List<Filter> filters) {
        // Extract query object in the filters, it must be an ElasitcFilter
        List<QueryHandler> elasticFilters = filters.stream()
                .filter(f -> f instanceof QueryHandler)
                .map(m -> (QueryHandler)m)
                .collect(Collectors.toList());

        List<Query> queries = elasticFilters.stream()
                .map(m -> m.query)
                .collect(Collectors.toList());

        this.query = BoolQuery.of(f -> f
                .should(queries))
                ._toQuery();

        children.addAll(filters);

        // Copy child error if any
        elasticFilters.stream().forEach(elasticFilter -> {this.addErrors(elasticFilter.getErrors());});
    }

    @Override
    public List<Filter> getChildren() {
        return children;
    }

    @Override
    public boolean evaluate(Object o) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor filterVisitor, Object o) {
        return null;
    }
}
