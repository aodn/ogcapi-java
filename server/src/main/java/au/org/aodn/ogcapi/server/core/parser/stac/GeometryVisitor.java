package au.org.aodn.ogcapi.server.core.parser.stac;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.locationtech.jts.geom.*;
import org.opengis.filter.spatial.Intersects;

@Slf4j
@Builder
public class GeometryVisitor extends DefaultFilterVisitor {

    @Override
    public Object visit(Intersects filter, Object data) {
        if(filter instanceof IntersectsImpl<?> impl) {
            if(impl.getGeometry().isPresent()) {
                if (data instanceof Polygon || data instanceof GeometryCollection) {
                    // To handle minor precision issues, try applying a small buffer (like 0.0) to clean up
                    // minor topology errors. This is a trick commonly used with JTS
                    return impl.getGeometry().get().intersection(((Geometry) data).buffer(0.0));
                }
                else  {
                    return data;
                }
            }
        }
        return null;
    }
}
