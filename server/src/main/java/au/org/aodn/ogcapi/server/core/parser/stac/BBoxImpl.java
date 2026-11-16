package au.org.aodn.ogcapi.server.core.parser.stac;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import au.org.aodn.ogcapi.server.core.util.BboxUtils;
import lombok.Getter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.api.filter.FilterVisitor;
import org.geotools.api.filter.MultiValuedFilter;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.Literal;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.api.filter.spatial.BBOX;
import org.geotools.api.geometry.BoundingBox;
import org.geotools.api.geometry.BoundingBox3D;
import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

public class BBoxImpl <T extends Enum<T> & CQLFieldsInterface> implements BBOX {

    @Getter
    protected Geometry geometry = null;

    protected BoundingBox bounds = null;

    public BBoxImpl(Expression geometry, Expression bounds, MultiValuedFilter.MatchAction matchAction) {
        if (bounds instanceof Literal literal) {
            Object value = literal.getValue();
            if (value instanceof BoundingBox3D boundingBox3D) {
                //TODO: Handle BoundingBox3D
            }
            else if (value instanceof Geometry g && geometry instanceof PropertyName name) {
                if (g.getUserData() instanceof CoordinateReferenceSystem crs) {
                    //TODO: Handle CRS
                }
            }
        }
    }

    public BBoxImpl(
            Expression e,
            double minx, double miny, double maxx, double maxy, String srs,
            MultiValuedFilter.MatchAction matchAction) {
        try {
            CoordinateReferenceSystem crs = null;
            CQLCrsType cqlCrsType = CQLCrsType.EPSG4326;
            if (srs != null && !srs.isEmpty()) {
                try {
                    crs = CRS.decode(srs);
                    cqlCrsType = CQLCrsType.convertFromUrl(srs);
                }
                catch (MismatchedDimensionException mde) {
                    throw new RuntimeException(mde);
                }
                catch (NoSuchAuthorityCodeException var16) {
                    crs = CRS.parseWKT(srs);
                }
            }
            this.bounds = new ReferencedEnvelope(minx, maxx, miny, maxy, crs);
            this.geometry = BboxUtils.normalizeBbox(minx, maxx, miny, maxy);

        } catch (FactoryException fe) {
            throw new RuntimeException("Failed to setup bbox SRS", fe);
        }

    }

    @Override
    public BoundingBox getBounds() {
        return this.bounds;
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
    public Object accept(FilterVisitor visitor, Object extraData) {
        return visitor.visit(this, extraData);
    }
}
