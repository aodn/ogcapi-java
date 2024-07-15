package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.And;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AndImpl extends Handler implements And {

    protected List<Filter> children = new ArrayList<>();

    public AndImpl(Filter filter1, Filter filter2) {

        if(filter1 == null && filter2 instanceof Handler elasticFilter2) {
            this.query = elasticFilter2.getQuery();
            this.addErrors(elasticFilter2.getErrors());
        }
        else if(filter2 == null && filter1 instanceof Handler elasticFilter1){
            this.query = elasticFilter1.getQuery();
            this.addErrors(elasticFilter1.getErrors());
        }
        else if(filter1 instanceof Handler elasticFilter1 && filter2 instanceof Handler elasticFilter2) {
            this.query = BoolQuery.of(f -> f
                    .filter(elasticFilter1.query, elasticFilter2.query)
            )._toQuery();

            // Remember to copy the error from child
            this.addErrors(elasticFilter1.getErrors());
            this.addErrors(elasticFilter2.getErrors());
        }
        children.add(filter1);
        children.add(filter2);
    }

    public AndImpl(List<Filter> filters) {
        // Extract query object in the filters, it must be an ElasitcFilter
        List<Handler> elasticFilters = filters.stream()
                .filter(Objects::nonNull)
                .filter(f -> f instanceof Handler)
                .map(m -> (Handler)m)
                .toList();

        List<Query> queries = elasticFilters.stream()
                .map(m -> m.query)
                .collect(Collectors.toList());

        this.query = BoolQuery.of(f -> f
                .filter(queries)
        )._toQuery();

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
