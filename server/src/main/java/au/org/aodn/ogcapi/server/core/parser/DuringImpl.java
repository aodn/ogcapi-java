package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.StacExtent;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.temporal.object.DefaultPeriod;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.temporal.During;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

public class DuringImpl<T extends Enum<T>> extends ElasticFilter implements During {
    protected Logger logger = LoggerFactory.getLogger(DuringImpl.class);

    protected Expression expression1;
    protected Expression expression2;


    public DuringImpl(Expression expression1, Expression expression2, Class<T> enumType) {

        this.expression1 = expression1;
        this.expression2 = expression2;

        if(expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            try {
                DefaultPeriod period = (DefaultPeriod) literal.getValue();
                this.query = NestedQuery.of(n -> n
                        .path(StacExtent.path)
                        .query(q1 -> q1
                                .range(r -> r
                                        .field(Enum.valueOf(enumType, attribute.toString()).toString())
                                        .gte(JsonData.of(dateFormatter.format(period.getBeginning().getPosition().getDate())))
                                        .lte(JsonData.of(dateFormatter.format(period.getEnding().getPosition().getDate())))
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