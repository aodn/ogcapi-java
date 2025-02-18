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
     * Always return data, the reason is that this GeometryVisitor only purpose is to consider the BBOX aka the view point
     * of the map and chop the spatial extents accordingly. The other INTERSECT, WITHIN ops isn't something we need to handle
     * here and therefore always return data
     * @param filter - Intersect filter
     * @param data - The income data
     * @return - Return same data
     */
    @Override
    public Object visit(Intersects filter, Object data) {
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
            if(impl.getBounds() != null && data instanceof Polygon || data instanceof GeometryCollection) {
                return impl.getGeometry().intersection(((Geometry) data).buffer(0.0));
            }
            else {
                return data;
            }
        }
        return null;
    }
}
