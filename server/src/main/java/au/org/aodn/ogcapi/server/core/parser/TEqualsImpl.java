package au.org.aodn.ogcapi.server.core.parser;

import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.temporal.TEquals;

/**
 * It means time:intervalEquals
 *
 * If a proper interval T1 is intervalEquals another proper interval T2, then the beginning of T1 is coincident with
 * the beginning of T2, and the end of T1 is coincident with the end of T2.
 *
 * TEQUALS <datetime> | <duration> is allowed,
 *
 * <duration> is like
 *  A time duration specified as P [ y Y m M d D ] T [ h H m M s S ]. The duration can be specified to any
 *  desired precision by including only the required year, month, day, hour, minute and second components.
 *  Examples: P1Y2M, P4Y2M20D, P4Y2M1DT20H3M36S
 *
 * @param <T>
 */
public class TEqualsImpl<T extends Enum<T>> extends Handler implements TEquals {

    protected Expression expression1;
    protected Expression expression2;

    public TEqualsImpl(Expression expression1, Expression expression2, Class<T> enumType) {
        this.expression1 = expression1;
        this.expression2 = expression2;

        // TODO: Pending implement, the parse accept TEQUALS date_time | duration/date_time but
        // the second one do not parse correctly
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
