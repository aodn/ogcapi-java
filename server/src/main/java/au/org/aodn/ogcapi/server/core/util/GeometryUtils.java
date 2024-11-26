package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

public class GeometryUtils {

    protected static final int PRECISION = 15;
    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    protected static ObjectMapper mapper = new ObjectMapper();
    // This number of decimal is needed to do some accurate
    protected static GeometryJSON json = new GeometryJSON(PRECISION);

    @Getter
    @Setter
    protected static int centroidScale = 5;

    // Create an ExecutorService with a fixed thread pool size
    @Getter
    @Setter
    protected static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);

    protected static Logger logger = LoggerFactory.getLogger(GeometryUtils.class);
    /**
     * Create a centroid point for the polygon, this will help to speed up the map processing as there is no need
     * to calculate large amount of data.
     * @param collection - The polygon that describe the spatial extents.
     * @return - The points that represent the centroid or use interior point if centroid is outside of the polygon.
     */
    public static List<List<BigDecimal>> createCentroid(Geometry collection) {
        try {

            // Flatten the map and extract all polygon, some of the income geometry is GeometryCollection
            List<Coordinate> coordinates = calculateGeometryCentroid(collection);

            if(coordinates != null) {
                return coordinates
                        .stream()
                        .filter(Objects::nonNull)
                        .map(coordinate -> List.of(
                                // We do not need super high precision for centroid, this help to speed up the transfer
                                // on large area
                                BigDecimal.valueOf(coordinate.getX()).setScale(getCentroidScale(), RoundingMode.HALF_UP),
                                BigDecimal.valueOf(coordinate.getY()).setScale(getCentroidScale(), RoundingMode.HALF_UP))
                        )
                        .toList();
            }
            else {
                return null;
            }
        }
        catch (Exception e) {
            return null;
        }
    }

    protected static List<Coordinate> calculateGeometryCentroid(Geometry geometry) {
        if(geometry instanceof GeometryCollection gc) {
            return calculateCollectionCentroid(gc);
        }
        else if(geometry instanceof Polygon pl) {
            return List.of(calculatePolygonCentroid(pl).getCoordinate());
        }
        else if(geometry instanceof LineString) {
            return List.of(geometry.getCentroid().getCoordinate());
        }
        else if(geometry instanceof Point p) {
            return List.of(p.getCoordinate());
        }
        else {
            logger.info("Skip geometry centroid for {}", geometry.getGeometryType());
            return null;
        }
    }

    protected static Point calculatePolygonCentroid(Geometry geometry) {
        // Make sure the point will not fall out of the shape, for example a U shape will make
        // centroid fall out of the U, so we check if the centroid is out of the shape? if yes then use
        // interior point
        return geometry.contains(geometry.getCentroid()) ?
                geometry.getCentroid() :
                geometry.getInteriorPoint();
    }

    protected static List<Coordinate> calculateCollectionCentroid(GeometryCollection geometryCollection) {
        // Try simplified the polygon to reduce the number of centroid.
        Geometry geometry = geometryCollection.union();

        try {
            Point centroid = calculatePolygonCentroid(geometry);
            return List.of(new Coordinate(centroid.getX(), centroid.getY()));
        }
        catch(Exception e) {
            // That means it cannot be simplified to a polygon that able to calculate centroid,
            // we need to calculate it one by one
            List<Coordinate> coordinates = new ArrayList<>();
            for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
                geometry = geometryCollection.getGeometryN(i);

                // Make sure the point will not fall out of the shape, for example a U shape will make
                // centroid fall out of the U, so we check if the centroid is out of the shape? if yes then use
                // interior point
                Point centroid = calculatePolygonCentroid(geometry);
                coordinates.add(new Coordinate(centroid.getX(), centroid.getY()));
            }
            return coordinates;
        }
    }
    /**
     * Create a grid based on the area of the spatial extents. Once we have the grid, we can union the area
     * @param envelope - An envelope that cover the area of the spatial extents
     * @param cellSize - How big each cell will be
     * @return - List of polygon that store the grid
     */
    protected static List<Polygon> createGridPolygons(Envelope envelope, double cellSize) {
        List<Polygon> gridPolygons = new ArrayList<>();

        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double maxX = envelope.getMaxX();
        double maxY = envelope.getMaxY();

        // Check if the cellSize is too small on both side, that is we need to split even if the
        // envelope have long width but short height etc
        if (cellSize <= 0 || (cellSize > (maxX - minX) && cellSize > (maxY - minY))) {
            Polygon itself = factory.createPolygon(new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(maxX, minY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(minX, maxY),
                    new Coordinate(minX, minY)  // Closing the polygon
            });
            gridPolygons.add(itself);
        }
        else {
            // Loop to create grid cells
            for (double x = minX; x < maxX; x += cellSize) {
                for (double y = minY; y < maxY; y += cellSize) {
                    // Create a polygon for each grid cell
                    Polygon gridCell = factory.createPolygon(new Coordinate[]{
                            new Coordinate(x, y),
                            new Coordinate(x + cellSize, y),
                            new Coordinate(x + cellSize, y + cellSize),
                            new Coordinate(x, y + cellSize),
                            new Coordinate(x, y)  // Closing the polygon
                    });
                    gridPolygons.add(gridCell);
                }
            }
        }
        return gridPolygons;
    }
    /**
     * Convert the WKT format from the cql to GeoJson use by Elastic search
     * @param literalExpression - Expression from parser
     * @return A Json string represent the literalExpression
     * @throws ParseException - Not expected to parse
     * @throws IOException - Not expected to parse
     */
    public static String convertToGeoJson(LiteralExpressionImpl literalExpression, CQLCrsType cqlCoorSystem) throws ParseException, IOException, FactoryException, TransformException {
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry geo = reader.read(literalExpression.toString());

        try(StringWriter writer = new StringWriter()) {
            Geometry t = CQLCrsType.transformGeometry(geo, cqlCoorSystem, CQLCrsType.EPSG4326);
            json.write(t, writer);

            String r = writer.toString();
            logger.debug("Converted to GeoJson {}", r);
            return r;
        }
    }
    /**
     * Please use this function as it contains the parser with enough decimal to make it work.
     * @param input - A Json of GeoJson
     * @return - An GeometryCollection that represent the GeoJson
     */
    public static Optional<Geometry> readGeometry(Object input) {
        try {
            if(!(input instanceof String)) {
                input = mapper.writeValueAsString(input);
            }
            return Optional.of(json.read(input));
        }
        catch (IOException e) {
            return Optional.empty();
        }
    }
    /**
     * Normalize a polygon by adjusting longitudes to the range [-180, 180], and return both parts as a GeometryCollection.
     *
     * @param polygon  The input polygon.
     * @return A polygon / multi-polygon unwrap at dateline.
     */
    public static Geometry normalizePolygon(Geometry polygon) {
        // Set dateline 180 check to true to unwrap a polygon across -180 line
        JtsGeometry jtsGeometry = new JtsGeometry(polygon, JtsSpatialContext.GEO, true, false);
        return jtsGeometry.getGeom();
    }

    public static Geometry createPolygon(double minx, double maxx, double miny, double maxy) {
        // Define the corners of the bounding box
        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(minx, maxy), // Top-Left
                new Coordinate(maxx, maxy), // Top-Right
                new Coordinate(maxx, miny), // Bottom-Right
                new Coordinate(minx, miny), // Bottom-Left
                new Coordinate(minx, maxy)  // Closing the polygon (back to Top-Left)
        };
        // Create a LinearRing (boundary of the polygon)
        LinearRing shell = factory.createLinearRing(coordinates);

        // Create the polygon (no holes)
        return factory.createPolygon(shell, null);
    }
}
