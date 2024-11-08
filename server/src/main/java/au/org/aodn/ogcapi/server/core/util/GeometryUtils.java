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

    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    protected static ObjectMapper mapper = new ObjectMapper();
    // This number of decimal is needed to do some accurate
    protected static GeometryJSON json = new GeometryJSON(15);

    @Getter
    @Setter
    protected static int centroidScale = 3;

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

    protected static List<List<Geometry>> splitAreaToGrid(List<List<Geometry>> geoList, final int gridSize) {
        return geoList.stream()
                .flatMap(Collection::stream)
                .map(i -> GeometryUtils.breakLargeGeometryToGrid(i, gridSize))
                .toList();
    }
    /**
     * Some geometry polygon cover the whole australia which is very big, it would be easier to process by UI
     * if we break it down in to grid of polygon. The grid size is hardcode here to 100.0, you can adjust it
     * but need to re-compile the code.
     * @param large - A Polygon to break into grid
     * @return - A polygon the break into grid.
     */
    protected static List<Geometry> breakLargeGeometryToGrid(final Geometry large, int gridSize) {
        logger.debug("Break down large geometry to grid {}", large);
        // Get the bounding box (extent) of the large polygon
        Envelope envelope = large.getEnvelopeInternal();

        // Hard code cell size, we can adjust the break grid size. 10.0 result in 3x3 grid
        // cover Australia
        List<Polygon> gridPolygons = createGridPolygons(envelope, gridSize);

        // List to store Future objects representing the results of the tasks
        List<Future<Geometry>> futureResults = new ArrayList<>();

        // Submit tasks to executor for each gridPolygon
        for (Polygon gridPolygon : gridPolygons) {
            Callable<Geometry> task = () -> {
                Geometry intersection = gridPolygon.intersection(large);
                return !intersection.isEmpty() ? intersection : null;
            };
            Future<Geometry> future = executorService.submit(task);
            futureResults.add(future);
        }

        // List to store the intersected polygons
        final List<Geometry> intersectedPolygons = new ArrayList<>();

        // Collect the results from the futures
        for (Future<Geometry> future : futureResults) {
            try {
                // This blocks until the result is available
                Geometry result = future.get();
                if (result != null) {
                    intersectedPolygons.add(result);
                }
            } catch (InterruptedException | ExecutionException e) {
                // Nothing to report
            }
        }
        return intersectedPolygons;
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
     * @param literalExpression
     * @return
     * @throws ParseException
     * @throws IOException
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
}
