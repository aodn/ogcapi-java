package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.locationtech.jts.io.ParseException;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.spatial.Intersects;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IntersectsImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements Intersects {

    protected Logger logger = LoggerFactory.getLogger(IntersectsImpl.class);

    protected Expression expression1;
    protected Expression expression2;

    public IntersectsImpl(Expression expression1, Expression expression2, Class<T> enumType, CQLCrsType cqlCrsType) {
        this.expression1 = expression1;
        this.expression2 = expression2;

        if(expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            try {
                String geojson = convertToGeoJson(literal, cqlCrsType);
                // Create elastic query here
                T v = Enum.valueOf(enumType, attribute.toString().toLowerCase());
                this.query = v.getIntersectsQuery(geojson);
            }
            catch (FactoryException | TransformException | ParseException | IOException e) {
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
    public boolean evaluate(Object o) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor filterVisitor, Object o) {
        return null;
    }
}
