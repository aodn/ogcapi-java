package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Or;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrImpl extends Handler implements Or {

    protected List<Filter> children = new ArrayList<>();

    public OrImpl(Filter filter1, Filter filter2) {
        if(filter1 instanceof Handler elasticFilter1 && filter2 instanceof Handler elasticFilter2) {
            this.query = BoolQuery.of(f -> f
                    .should(elasticFilter1.query, elasticFilter2.query)
            )._toQuery();

            children.add(filter1);
            children.add(filter2);

            // Remember to copy the error from child
            this.addErrors(elasticFilter1.getErrors());
            this.addErrors(elasticFilter2.getErrors());
        }
    }

    public OrImpl(List<Filter> filters) {
        // Extract query object in the filters, it must be an ElasitcFilter
        List<Handler> elasticFilters = filters.stream()
                .filter(f -> f instanceof Handler)
                .map(m -> (Handler)m)
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
