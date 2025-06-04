package au.org.aodn.ogcapi.server.core.parser.stac;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.locationtech.jts.geom.*;
import org.opengis.filter.spatial.*;

@Slf4j
@Builder
public class GeometryVisitor extends DefaultFilterVisitor {
    /**
     * If user used intersect, then we make sure we reduce the geometry to within the intersect area
     * so later centroid is always within the intersect area
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data if no filter set, or the geometry and only intersect with the CQL intersect geometry
     */
    @Override
    public Object visit(Intersects filter, Object data) {
        if(filter instanceof IntersectsImpl<?> impl) {
            if(data instanceof Polygon || data instanceof GeometryCollection) {
                if(impl.getGeometry().isPresent()) {
                    // The getGeometry return the INTERSECT() geometry area, so we make a intersection
                    // with the Object data
                    Geometry input = impl.getGeometry().get();
                    try {
                        return input.intersection(input);
                    }
                    catch(Exception e) {
                        // buffer is expensive to fix invalid geometry, call it only if needed
                        return input.intersection(input.buffer(0.0));
                    }
                }
                else {
                    return data;
                }
            }
        }
        return null;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Within filter, Object data) {
        return data;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Touches filter, Object data) {
        return data;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Overlaps filter, Object data) {
        return data;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Disjoint filter, Object data) {
        return data;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Crosses filter, Object data) {
        return data;
    }
    /**
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Beyond filter, Object data) {
        return data;
    }

    @Override
    public Object visit(BBOX filter, Object data) {
        if(filter instanceof BBoxImpl<?> impl) {
            if(impl.getGeometry() != null && (data instanceof Polygon || data instanceof GeometryCollection)) {
                Geometry input = (Geometry) data;
                try {
                    return impl.getGeometry().intersection(input);
                }
                catch(Exception e) {
                    // buffer is expensive to fix invalid geometry, call it only if needed
                    return impl.getGeometry().intersection(input.buffer(0.0));
                }
            }
            else {
                return data;
            }
        }
        return null;
    }
}
