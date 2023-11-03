package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.StacExtent;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.temporal.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch._types.query_dsl.*;

import java.text.SimpleDateFormat;

public class AfterImpl<T extends Enum<T>> extends ElasticFilter implements After {
    protected Logger logger = LoggerFactory.getLogger(AfterImpl.class);

    protected Expression expression1;
    protected Expression expression2;


    public AfterImpl(Expression expression1, Expression expression2, Class<T> enumType) {

        this.expression1 = expression1;
        this.expression2 = expression2;

        if(expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            try {
                this.query = NestedQuery.of(n -> n
                    .path(StacExtent.path)
                    .query(q1 -> q1
                        .range(r -> r
                            .field(Enum.valueOf(enumType, attribute.toString().toLowerCase()).toString())
                            .gte(JsonData.of(dateFormatter.format(literal.getValue())))
                            .format("strict_date_optional_time")
                        )
                    )
                )._toQuery();
            } catch (Exception e) {
                logger.warn("Exception in parsing, query result will be wrong", e);
                this.query = null;
            }
        }
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
    public boolean evaluate(Object object) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor visitor, Object extraData) {
        return null;
    }
}
