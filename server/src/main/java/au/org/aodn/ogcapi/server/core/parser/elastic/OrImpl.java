package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Or;

import java.util.ArrayList;
import java.util.List;

public class OrImpl extends QueryHandler implements Or {

    protected List<Filter> children = new ArrayList<>();

    private static boolean containsElasticSetting(Filter filter) {
        if (filter instanceof ElasticSetting) {
            return true;
        }

        return filter instanceof OrImpl orFilter
                && orFilter.getChildren().stream().anyMatch(OrImpl::containsElasticSetting);
    }

    /**
     * Recursively extracts leaf Elasticsearch queries from nested OR filters and returns them as a flat list.
     * The caller uses this list to construct a single bool/should query.
     */
    private static List<Query> collectQueries(Filter filter) {
        if (filter instanceof OrImpl orFilter) {
            // Keep collapsed membership queries (e.g. dataset_group IN) intact; flatten bool/should only.
            if (orFilter.getQuery() != null && !orFilter.getQuery().isBool()) {
                return List.of(orFilter.getQuery());
            }
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
     * GeoTools compiles {@code property IN (v1, v2)} as {@code or(equals...)} even for one value.
     * If every child is equality on the same field that implements membership, emit that IN query.
     */
    private static Query collapseToInQuery(List<Filter> filters) {
        if (filters.isEmpty()) {
            return null;
        }

        List<PropertyEqualToImpl<?>> equals = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter instanceof PropertyEqualToImpl<?> equal
                    && equal.getField() != null
                    && equal.getLiteralValue() != null) {
                equals.add(equal);
            } else {
                return null;
            }
        }

        CQLFieldsInterface field = equals.get(0).getField();
        if (equals.stream().anyMatch(equal -> equal.getField() != field)) {
            return null;
        }

        return field.getPropertyInQuery(equals.stream()
                .map(PropertyEqualToImpl::getLiteralValue)
                .toList());
    }


    /**
     * Builds the Elasticsearch representation of an OR expression.
     * A single query is returned directly. Multiple queries are combined into
     * one flat bool/should query to avoid deeply nested bool queries for large
     * vocabulary selections.
     */
    private void buildQuery(List<Filter> filters) {
        if (filters.stream().anyMatch(OrImpl::containsElasticSetting)) {
            throw new IllegalArgumentException("Or combine with query setting do not make sense");
        }

        Query inQuery = collapseToInQuery(filters);
        if (inQuery != null) {
            this.query = inQuery;
            return;
        }

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
