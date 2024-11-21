package au.org.aodn.ogcapi.server.core.parser.stac;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.spatial.Intersects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Slf4j
public class IntersectsImpl<T extends Enum<T> & CQLFieldsInterface> implements Intersects {

    protected Logger logger = LoggerFactory.getLogger(IntersectsImpl.class);

    protected Expression expression1;
    protected Expression expression2;

    @Getter
    protected Optional<Geometry> geometry = Optional.empty();

    public IntersectsImpl(Expression expression1, Expression expression2, CQLCrsType cqlCrsType) {
        if(expression1 instanceof AttributeExpressionImpl attribute && expression2 instanceof LiteralExpressionImpl literal) {
            this.expression1 = attribute;
            this.expression2 = literal;

            try {
                String geojson = GeometryUtils.convertToGeoJson(literal, cqlCrsType);
                geometry = GeometryUtils
                        .readGeometry(geojson)
                        .map(g -> GeometryUtils.normalizePolygon(g));
            }
            catch(Exception ex) {
                logger.warn("Exception in parsing, query result will be wrong", ex);
            }
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
    public MatchAction getMatchAction() {
        return null;
    }

    @Override
    public boolean evaluate(Object o) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor visitor, Object extraData) {
        return visitor.visit(this, extraData);
    }
}
