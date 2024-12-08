package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import au.org.aodn.ogcapi.server.core.util.BboxUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import co.elastic.clients.elasticsearch._types.TopLeftBottomRightGeoBounds;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope3D;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.MultiValuedFilter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.geometry.BoundingBox;
import org.opengis.geometry.BoundingBox3D;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import java.util.List;

/**
 * This class support both 2D or 3D query, but now we just implement 2D and support very limited operation for CQL
 * which is sufficient for init run.
 * example: BBOX(geometry, 105.08984375000037, -45.407201919778046, 163.91015625000117, -5.592798080220254)
 * @param <T>
 */
public class BBoxImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements BBOX {
    protected static final GeometryFactory factory = JTSFactoryFinder.getGeometryFactory();

    protected Expression geometry = null;
    protected BoundingBox bounds = null;
    protected MultiValuedFilter.MatchAction matchAction = MatchAction.ANY;

    public BBoxImpl(Expression geometry,
                    BoundingBox bounds,
                    MultiValuedFilter.MatchAction matchAction,
                    Class<T> enumType) {

        if(bounds instanceof BoundingBox3D box3D) {
            this.create3DCQL(geometry, box3D, matchAction);
        }
        else {
            this.create2DCQL(geometry, List.of(bounds), matchAction, enumType);
        }
    }

    public BBoxImpl(Expression geometry, Expression bounds, MultiValuedFilter.MatchAction matchAction) {
        if (bounds instanceof Literal literal) {
            Object value = literal.getValue();
            if (value instanceof BoundingBox3D boundingBox3D) {
                this.create3DCQL(geometry, boundingBox3D, matchAction);
            }
            else if (value instanceof Geometry g && geometry instanceof PropertyName name) {
                if (g.getUserData() instanceof CoordinateReferenceSystem crs) {
                    this.create3DCQL(name, (ReferencedEnvelope3D) JTS.bounds(g, crs), matchAction);
                }
            }
        }
    }

    public BBoxImpl(
            Expression e,
            double minx, double miny, double maxx, double maxy, String srs,
            MultiValuedFilter.MatchAction matchAction,
            Class<T> enumType) {
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
            } else {
                crs = null;
            }
            // This record the bounding box only, since the box may cross meridian, we need to split the polygon
            this.bounds = new ReferencedEnvelope(minx, maxx, miny, maxy, crs);

            // We need to handle anti-meridian, we normalize the polygon and may split into two polygon to cover
            // two area due to crossing -180 <> 180 line, this is because elastic geo_bounding_box assume [180, -180]
            Geometry g = BboxUtils.normalizeBbox(minx, maxx, miny, maxy);
            this.create2DCQL(e, GeometryUtils.toReferencedEnvelope(g,crs) , matchAction, enumType);

        } catch (FactoryException fe) {
            throw new RuntimeException("Failed to setup bbox SRS", fe);
        }

    }

    protected void create2DCQL(
            Expression geometry,
            List<? extends BoundingBox> bounds,
            MultiValuedFilter.MatchAction matchAction,
            Class<T> enumType) {

        this.matchAction = matchAction;
        this.geometry = geometry;
        final T v = Enum.valueOf(enumType, geometry.toString().toLowerCase());

        if(bounds.size() > 1) {
            // Handle multiple bounds by wrapping query with bool:should[]
            this.query = BoolQuery.of(f -> f.should(bounds.stream().map(boundingBox -> v.getBoundingBoxQuery(
                            TopLeftBottomRightGeoBounds.of(builder -> builder
                                    .topLeft(i -> i.latlon(ll -> ll.lon(boundingBox.getMinX()).lat(boundingBox.getMaxY())))
                                    .bottomRight(i -> i.latlon(ll -> ll.lon(boundingBox.getMaxX()).lat(boundingBox.getMinY())))
                            )))
                            .toList()))
                    ._toQuery();
        }
        else {
            this.query = v.getBoundingBoxQuery(
                    TopLeftBottomRightGeoBounds.of(builder -> builder
                            .topLeft(i -> i.latlon(ll -> ll.lon(bounds.get(0).getMinX()).lat(bounds.get(0).getMaxY())))
                            .bottomRight(i -> i.latlon(ll -> ll.lon(bounds.get(0).getMaxX()).lat(bounds.get(0).getMinY())))));
        }
    }

    protected void create3DCQL(Expression geometry, BoundingBox3D bounds, MultiValuedFilter.MatchAction matchAction) {
        this.matchAction = matchAction;

    }

    @Override
    public BoundingBox getBounds() {
        return bounds;
    }

    @Override
    public Expression getExpression1() {
        return geometry;
    }

    @Override
    public Expression getExpression2() {
        return null;
    }

    @Override
    public MatchAction getMatchAction() {
        return this.matchAction;
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
