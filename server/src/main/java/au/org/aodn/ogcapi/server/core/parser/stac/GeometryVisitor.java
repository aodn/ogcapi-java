package au.org.aodn.ogcapi.server.core.parser.stac;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.opengis.filter.spatial.Intersects;
import org.locationtech.jts.geom.Geometry;

@Slf4j
@Builder
public class GeometryVisitor extends DefaultFilterVisitor {

    @Override
    public Object visit(Intersects filter, Object data) {
        if(filter instanceof IntersectsImpl<?> impl && data instanceof Geometry geo) {
            if(impl.getPreparedGeometry().isPresent()) {
                return impl.getPreparedGeometry()
                        .get()
                        .getGeometry()
                        .intersection(geo);
            }
        }
        return null;
    }
}
