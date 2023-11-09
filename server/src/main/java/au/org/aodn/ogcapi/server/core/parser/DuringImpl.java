package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCollectionsField;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacSummeries;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.temporal.object.DefaultPeriod;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.temporal.During;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support temporal field only, we use query similar to this to do the scan of individual temporal range inside
 * the summaries.temporal field
 *
 *  "nested": {
 *             "path": "summaries.temporal",
 *             "query": {
 *               "bool": {
 *                 "must": [
 *                   {
 *                     "range": {
 *                       "summaries.temporal.start": {
 *                         "lte": "2020-12-25T00:00:00"
 *                       }
 *                     }
 *                   },
 *                   {
 *                     "range": {
 *                       "summaries.temporal.end": {
 *                         "gte": "2020-12-22T00:00:00"
 *                       }
 *                     }
 *                   }
 *                 ]
 *               }
 *             }
 *           }
 *         }
 * @param <T>
 */
public class DuringImpl<T extends Enum<T>> extends ElasticFilter implements During {
    protected Logger logger = LoggerFactory.getLogger(DuringImpl.class);

    protected Expression expression1;
    protected Expression expression2;


    public DuringImpl(Expression expression1, Expression expression2, Class<T> enumType) {

        this.expression1 = expression1;
        this.expression2 = expression2;

        if(expression1 instanceof AttributeExpressionImpl attribute
                && expression2 instanceof LiteralExpressionImpl literal
                && literal.getValue() instanceof DefaultPeriod period) {

            try {
                T type = Enum.valueOf(enumType, attribute.toString().toLowerCase());
                if(type instanceof CQLCollectionsField cqlCollectionsField
                        && cqlCollectionsField == CQLCollectionsField.temporal) {

                    Query gte = RangeQuery.of(r -> r
                                    .field(StacSummeries.TemporalStart.field)
                                    .gte(JsonData.of(dateFormatter.format(period.getBeginning().getPosition().getDate())))
                                    .format("strict_date_optional_time"))._toQuery();

                    Query lte = RangeQuery.of(r -> r
                            .field(StacSummeries.TemporalEnd.field)
                            .lte(JsonData.of(dateFormatter.format(period.getEnding().getPosition().getDate())))
                            .format("strict_date_optional_time"))._toQuery();


                    this.query = NestedQuery.of(n -> n
                            .path(StacSummeries.Temporal.field)
                                .query(BoolQuery.of(q -> q
                                        .must(gte, lte))._toQuery()
                            )
                    )._toQuery();
                }
                else {
                    this.addError(new CQLException("DURING operation not support for non temporal field"));
                }
            }
            catch (Exception e) {
                this.addError(new CQLException("Exception in parsing, query result will be wrong", e.getMessage()));
            }
        }
        else {
            this.addError(new CQLException("Non support argument for DURING operation, require attribute and period"));
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
