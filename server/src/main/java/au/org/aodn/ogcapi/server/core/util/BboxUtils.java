package au.org.aodn.ogcapi.server.core.util;

import org.locationtech.jts.geom.*;

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
    public static MultiPolygon normalizeBbox(double minx, double maxx, double miny, double maxy) {
        // A span of 360 or more covers the whole world. Incoming longitudes may sit outside [-180, 180].
        if ((maxx - minx) >= 360) {
            minx = -180;
            maxx = 180;
        } else {
            // Shift into one 360-degree window with minx in [-180, 180). A viewport that
            // continues past the date line (for example 112 to 188.9) then has maxx > 180,
            // and the wrapped east edge is maxx - 360 (188.9 -> -171.1), not -(maxx - 180).
            while (minx < -180) {
                minx += 360;
                maxx += 360;
            }
            while (minx >= 180) {
                minx -= 360;
                maxx -= 360;
            }
        }

        List<Polygon> polygons = new ArrayList<>();
        if (maxx <= 180) {
            polygons.add((Polygon) createBoxPolygon(minx, maxx, miny, maxy));
        } else {
            polygons.add((Polygon) createBoxPolygon(minx, 180, miny, maxy));
            polygons.add((Polygon) createBoxPolygon(-180, maxx - 360, miny, maxy));
        }
        return GeometryUtils.getFactory().createMultiPolygon(polygons.toArray(new Polygon[0]));
    }

    protected static Geometry createBoxPolygon(double startX, double endX, double startY, double endY) {
        // If the longitude range crosses the anti-meridian (e.g., maxx > 180)
        // Normal case have not cross dateline
        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(startX, startY),   // Bottom-left corner
                new Coordinate(endX, startY),   // Bottom-right corner
                new Coordinate(endX, endY),   // Top-right corner
                new Coordinate(startX, endY),   // Top-left corner
                new Coordinate(startX, startY)    // Closing the loop (bottom-left corner)
        };

        // Create a LinearRing for the boundary of the Polygon
        LinearRing ring = GeometryUtils.getFactory().createLinearRing(coordinates);

        // Create the Polygon using the LinearRing (no holes for simplicity)
        return GeometryUtils.getFactory().createPolygon(ring, null);
    }
}
