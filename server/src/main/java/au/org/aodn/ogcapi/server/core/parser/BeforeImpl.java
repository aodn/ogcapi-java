package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.temporal.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Only works for temporal field for now
 *
 * @param <T>
 */
public class BeforeImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements Before {
    protected Logger logger = LoggerFactory.getLogger(BeforeImpl.class);

    protected Expression expression1;
    protected Expression expression2;

    public BeforeImpl(Expression expression1, Expression expression2, Class<T> enumType) {

        this.expression1 = expression1;
        this.expression2 = expression2;

        if(expression1 instanceof AttributeExpressionImpl attribute
                && expression2 instanceof LiteralExpressionImpl literal) {

            try {
                T type = Enum.valueOf(enumType, attribute.toString().toLowerCase());
                if(type instanceof CQLFields cqlFields
                        && cqlFields == CQLFields.temporal) {

                    this.query = NestedQuery.of(n -> n
                            .path(StacSummeries.Temporal.searchField)
                            .query(q1 -> q1
                                    .range(r -> r
                                            .field(StacSummeries.TemporalEnd.searchField)
                                            .lte(JsonData.of(dateFormatter.format(literal.getValue())))
                                            .format("strict_date_optional_time")
                                    )
                            )
                    )._toQuery();
                }
                else {
                    this.addErrors(new CQLException("BEFORE operation not support for non temporal field"));
                }
            }
            catch (Exception e) {
                this.addErrors(new CQLException("Exception in parsing, query result will be wrong", e.getMessage()));
            }
        }
        else {
            this.addErrors(new CQLException("Non support argument for BEFORE operation, require attribute and datetime"));
        }
    }

    @Override
    public Expression getExpression1() {
        return this.expression1;
    }

    @Override
    public Expression getExpression2() {
        return this.expression2;
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
