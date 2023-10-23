package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.spatial.Intersects;

public class IntersectsImpl extends ElasticFilter implements Intersects {

    protected Expression expression1;
    protected Expression expression2;

    public IntersectsImpl(Expression expression1, Expression expression2, Query query) {
        this.query = query;
        this.expression1 = expression1;
        this.expression2 = expression2;
    }

    @Override
    public Expression getExpression1() {
        return null;
    }

    @Override
    public Expression getExpression2() {
        return null;
    }

    @Override
    public MatchAction getMatchAction() {
        return null;
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
