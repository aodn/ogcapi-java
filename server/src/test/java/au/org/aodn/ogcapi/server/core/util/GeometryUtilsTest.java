package au.org.aodn.ogcapi.server.core.util;

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static au.org.aodn.ogcapi.server.BaseTestClass.readResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeometryUtilsTest {

    protected GeometryCollection convertToGeometryCollection(
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
        // Create a GeometryFactory
        GeometryFactory geometryFactory = new GeometryFactory();

        // List to hold the geometries extracted from the FeatureCollection
        List<Geometry> geometries = new ArrayList<>();

        // Iterate through the FeatureCollection and extract geometries
        try (FeatureIterator<SimpleFeature> features = featureCollection.features()) {
            while (features.hasNext()) {
                Geometry geometry = (Geometry) (features.next()).getDefaultGeometry();
                geometries.add(geometry);
            }
        }

        // Convert the list of geometries to an array
        Geometry[] geometryArray = geometries.toArray(new Geometry[0]);

        // Create and return a GeometryCollection from the array of geometries
        return geometryFactory.createGeometryCollection(geometryArray);
    }

    /**
     * Given a irregular geojson, with hole the centroid point is still inside the polygon
     *
     * @throws IOException - Not expected to throw this
     */
    @Test
    @SuppressWarnings("unchecked")
    public void verifyCentroidCorrect() throws IOException {
        // You can paste the geojson on geojson.io to see what it looks like
        String geojson = readResourceFile("classpath:canned/irregular.geojson");
        FeatureJSON json = new FeatureJSON();

        // Read the GeoJSON file
        StringReader reader = new StringReader(geojson);
        FeatureCollection<SimpleFeatureType, SimpleFeature> feature = json.readFeatureCollection(reader);

        List<Coordinate> point = GeometryUtils.calculateCollectionCentroid(convertToGeometryCollection(feature));
        assertEquals(1, point.size(), "One item");
        assertEquals(2.805438932281021, point.get(0).getX(), "X");
        assertEquals(2.0556251797475227, point.get(0).getY(), "Y");
    }

    @Test
    public void verifyCreateGirdPolygonWithValidCellSize() {
        Envelope envelope = new Envelope(0, 10, 0, 10);  // 10x10 envelope
        double cellSize = 2.0; // Valid cell size

        List<Polygon> gridPolygons = GeometryUtils.createGridPolygons(envelope, cellSize);

        // Check that the grid has been divided into cells
        Assertions.assertFalse(gridPolygons.isEmpty(), "Expected non-empty grid polygons list");

        // Check the number of cells created (should be 5x5 = 25 for a 10x10 grid with cell size 2)
        Assertions.assertEquals(25, gridPolygons.size(), "Expected 25 grid cells");

        // Check that each cell is a valid polygon and has the expected cell size
        gridPolygons.forEach(polygon -> {
            Assertions.assertNotNull(polygon, "Expected each grid cell to be a valid polygon");
            Assertions.assertTrue(polygon.getArea() <= (cellSize * cellSize), "Expected each cell to have an area <= cellSize^2");
        });
    }

    @Test
    public void verifyCreateGirdPolygonWithTooBigCellSize() {
        Envelope envelope = new Envelope(0, 10, 0, 10);  // 10x10 envelope
        Polygon polygon = GeometryUtils.factory.createPolygon(new Coordinate[]{
                new Coordinate(envelope.getMinX(), envelope.getMinY()),
                new Coordinate(envelope.getMaxX(), envelope.getMinY()),
                new Coordinate(envelope.getMaxX(), envelope.getMaxY()),
                new Coordinate(envelope.getMinX(), envelope.getMaxY()),
                new Coordinate(envelope.getMinX(), envelope.getMinY())  // Closing point
        });
        double cellSize = 20;  // Too small cell size

        // Verify that an exception is thrown for too small cell size
        List<Polygon> gridPolygons = GeometryUtils.createGridPolygons(envelope, cellSize);

        // Check that the exception message is as expected
        Assertions.assertEquals(1, gridPolygons.size(), "Get 1 back");
        Assertions.assertTrue(gridPolygons.get(0).equalsExact(polygon), "Get back itself");
    }

    @Test
    public void verifyConvertToWktWithNullGeometry() {
        // Test with null input
        String result = GeometryUtils.convertToWkt(null);
        Assertions.assertNull(result, "Expected null for null input");
    }

    @Test
    public void verifyConvertToWktWithNonSpecifiedMultiPolygon() {
        // Test with NON_SPECIFIED_MULTIPOLYGON constant value
        String result = GeometryUtils.convertToWkt(GeometryUtils.NON_SPECIFIED_MULTIPOLYGON);
        Assertions.assertNull(result, "Expected null for non-specified multipolygon");
    }

}
