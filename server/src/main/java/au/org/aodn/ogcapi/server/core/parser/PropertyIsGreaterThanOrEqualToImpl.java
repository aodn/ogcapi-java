package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.MultiValuedFilter;
import org.opengis.filter.PropertyIsGreaterThanOrEqualTo;
import org.opengis.filter.expression.Expression;

public class PropertyIsGreaterThanOrEqualToImpl<T extends Enum<T>> extends QueryHandler implements PropertyIsGreaterThanOrEqualTo {

    protected Expression expression1;
    protected Expression expression2;
    protected Boolean isMatchingCase;
    protected MultiValuedFilter.MatchAction matchAction;

    public PropertyIsGreaterThanOrEqualToImpl(Expression expression1, Expression expression2, boolean isMatchingCase, MultiValuedFilter.MatchAction matchAction, Class<T> enumType) {
        this.expression1 = expression1;
        this.expression2 = expression2;
        this.isMatchingCase = isMatchingCase;
        this.matchAction = matchAction;

        if (expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            this.query = RangeQuery.of(builder -> builder
                    .field(Enum.valueOf(enumType, attribute.toString().toLowerCase()).toString())
                    .gte(JsonData.of(literal.toString()))
            )._toQuery();
        }
    }

    @Override
    public Expression getExpression1() {
        return expression1;
    }

    @Override
    public Expression getExpression2() {
        return expression2;
    }

    @Override
    public boolean isMatchingCase() {
        return isMatchingCase;
    }

    @Override
    public MatchAction getMatchAction() {
        return matchAction;
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
