package au.org.aodn.ogcapi.server.core.parser.elastic;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Or;

import java.util.ArrayList;
import java.util.List;

public class OrImpl extends QueryHandler implements Or {

    protected List<Filter> children = new ArrayList<>();

    /**
     * Recursively extracts leaf Elasticsearch queries from nested OR filters and returns them as a flat list.
     * The caller uses this list to construct a single bool/should query.
     */
    private static List<Query> collectQueries(Filter filter) {
        if (filter instanceof OrImpl orFilter) {
            return orFilter.getChildren().stream()
                    .flatMap(child -> collectQueries(child).stream())
                    .toList();
        }

        if (filter instanceof QueryHandler handler && handler.getQuery() != null) {
            return List.of(handler.getQuery());
        }

        return List.of();
    }


    /**
     * Builds the Elasticsearch representation of an OR expression.
     *
     * A single query is returned directly. Multiple queries are combined into
     * one flat bool/should query to avoid deeply nested bool queries for large
     * vocabulary selections.
     */
    private void buildQuery(List<Filter> filters) {
        List<Query> queries = filters.stream()
                .flatMap(filter -> collectQueries(filter).stream())
                .toList();

        if (queries.size() == 1) {
            this.query = queries.get(0);
        } else if (!queries.isEmpty()) {
            this.query = BoolQuery.of(b -> b.should(queries))._toQuery();
        }
    }

    public OrImpl(Filter filter1, Filter filter2) {
        children.add(filter1);
        children.add(filter2);

        buildQuery(children);

        if (filter1 instanceof QueryHandler handler) {
            addErrors(handler.getErrors());
        }
        if (filter2 instanceof QueryHandler handler) {
            addErrors(handler.getErrors());
        }
    }

    // Flatten the bool should query so that to avoid when many parameters are selected,
    // the nested query cause Elasticsearch error
    public OrImpl(List<Filter> filters) {
        children.addAll(filters);
        buildQuery(children);

        filters.stream()
                .filter(QueryHandler.class::isInstance)
                .map(QueryHandler.class::cast)
                .forEach(handler -> addErrors(handler.getErrors()));
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
