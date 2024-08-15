package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.expression.Expression;

public class IsNullImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements PropertyIsNull {

    protected Expression expression;

    public IsNullImpl(Expression expression, Class<T> enumType) {
        this.expression = expression;
        T v = Enum.valueOf(enumType, expression.toString().toLowerCase());
        this.query = v.getIsNullQuery();
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
