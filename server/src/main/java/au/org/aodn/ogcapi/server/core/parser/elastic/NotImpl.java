package au.org.aodn.ogcapi.server.core.parser.elastic;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Not;

public class NotImpl extends QueryHandler implements Not {

    public NotImpl(QueryHandler filter) {
        this.query = BoolQuery.of(b -> b
                        .mustNot(filter.getQuery()))
                ._toQuery();

        this.addErrors(filter.getErrors());
    }

    @Override
    public Filter getFilter() {
        return this;
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
