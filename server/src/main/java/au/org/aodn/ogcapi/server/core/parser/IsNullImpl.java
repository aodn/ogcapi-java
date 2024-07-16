package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.expression.Expression;

public class IsNullImpl<T extends Enum<T>> extends QueryHandler implements PropertyIsNull {

    protected Expression expression;

    public IsNullImpl(Expression expression, Class<T> enumType) {
        this.expression = expression;
        Query fieldExist = ExistsQuery.of(f -> f
                            .field(Enum.valueOf(enumType, expression.toString()).toString()))._toQuery();

        query = BoolQuery.of(b -> b
                .mustNot(fieldExist))._toQuery();
    }

    @Override
    public Expression getExpression() {
        return expression;
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
