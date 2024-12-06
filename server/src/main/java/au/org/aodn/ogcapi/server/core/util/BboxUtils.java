package au.org.aodn.ogcapi.server.core.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for BBox operation only, it assumes the incoming is a bounding box
 */
public class BboxUtils {
    /**
     * Normalize a bbox by adjusting longitudes to the range [-180, 180], and then split it into two if it
     * is cross meridian. We need this because Bbox is assumed to work within this range only.
     * @param minx - left
     * @param maxx - right
     * @param miny - top
     * @param maxy - bottom
     * @return - Geometry which is bounded [-180, 180]
     */
    public static Geometry normalizeBbox(double minx, double maxx, double miny, double maxy) {
        // Bounding check, if greater than 360 already cover whole world, so adjust the maxx to something
        // meaningful, noted that minx and maxx is not normalized yet so can be anything even beyond 180
        if((maxx - minx) >= 360) {
            // Value does not matter, it covered whole world which is equal to
            minx = -180;
            maxx = 180;
        }
        minx = (minx < -180) ? 180 - Math.abs(180 + minx) : minx;
        maxx = (maxx > 180) ? -(maxx - 180) : maxx;

        // Normalized the box, so it is within [-180, 180]
        List<Polygon> polygons = new ArrayList<>();
        if(maxx >= 0 && maxx <= 180) {
            // Normal case
            polygons.add((Polygon)createBoxPolygon(minx, maxx, miny, maxy));
        }
        else {
            polygons.add((Polygon)createBoxPolygon(minx, 180, miny, maxy));
            polygons.add((Polygon)createBoxPolygon(-180, maxx, miny, maxy));
        }
        return GeometryUtils.getFactory().createMultiPolygon(polygons.toArray(new Polygon[0]));
    }

    protected static Geometry createBoxPolygon(double minx, double maxx, double miny, double maxy) {
        // If the longitude range crosses the anti-meridian (e.g., maxx > 180)
        // Normal case have not cross dateline
        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(minx, miny),   // Bottom-left corner
                new Coordinate(maxx, miny),   // Bottom-right corner
                new Coordinate(maxx, maxy),   // Top-right corner
                new Coordinate(minx, maxy),   // Top-left corner
                new Coordinate(minx, miny)    // Closing the loop (bottom-left corner)
        };

        // Create a LinearRing for the boundary of the Polygon
        LinearRing ring = GeometryUtils.getFactory().createLinearRing(coordinates);

        // Create the Polygon using the LinearRing (no holes for simplicity)
        return GeometryUtils.getFactory().createPolygon(ring, null);
    }
}
