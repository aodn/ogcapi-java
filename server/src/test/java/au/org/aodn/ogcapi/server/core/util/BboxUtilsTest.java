package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static au.org.aodn.ogcapi.server.BaseTestClass.readResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BboxUtilsTest {
    /**
     * East edge 314.173 wraps to 314.173 - 360 = -45.827, so the box runs
     * -4.6 -> 180 and -180 -> -45.827.
     */
    @Test
    public void verifyNormalizeBbox1() {
        Geometry n = BboxUtils.normalizeBbox(-4.614697916267209,314.17320304524225,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(-4.614697916267209, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        Polygon expect3 = (Polygon)BboxUtils.createBoxPolygon(-180, -45.826796954757754, -76.58044236400484, 60.83962132913365);
        assertEquals(expect3, n.getGeometryN(1), "Second polygon");
    }
    /**
     * East edge 314.173 wraps to -45.827, so the box runs 10 -> 180 and -180 -> -45.827.
     */
    @Test
    public void verifyNormalizeBbox2() {
        Geometry n = BboxUtils.normalizeBbox(10,314.17320304524225,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(10, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        Polygon expect2 = (Polygon)BboxUtils.createBoxPolygon(-180, -45.826796954757754, -76.58044236400484, 60.83962132913365);
        assertEquals(expect2, n.getGeometryN(1), "Second polygon");
    }
    /**
     * The maxx is so big that it cover whole world a few times
     */
    @Test
    public void verifyNormalizeBboxVeryBigMaxX() {
        Geometry n = BboxUtils.normalizeBbox(10,600,-76.58044236400484,60.83962132913365);
        assertEquals(1, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(-180, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon min 10");

        // Same no matter how small the minx is
        n = BboxUtils.normalizeBbox(-200,600,-76.58044236400484,60.83962132913365);
        assertEquals(1, n.getNumGeometries(), "Size correct");

        expect1 = (Polygon)BboxUtils.createBoxPolygon(-180, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon min -200");
    }
    /**
     * The minx is so small
     */
    @Test
    public void verifyNormalizeBboxSmallMinX() {
        Geometry n = BboxUtils.normalizeBbox(-250,-60,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(110, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        Polygon expect2 = (Polygon)BboxUtils.createBoxPolygon(-180, -60, -76.58044236400484, 60.83962132913365);
        assertEquals(expect2, n.getGeometryN(1), "Second polygon");
        // -351 = 9 after minus 180
        n = BboxUtils.normalizeBbox(-351,-60,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        expect1 = (Polygon)BboxUtils.createBoxPolygon(9, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        expect2 = (Polygon)BboxUtils.createBoxPolygon(-180, -60, -76.58044236400484, 60.83962132913365);
        assertEquals(expect2, n.getGeometryN(1), "Second polygon");
        // -361 = -1 after minus 180, and -2 for maxx will not cover the whole world
        n = BboxUtils.normalizeBbox(-361,-2, -76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        expect1 = (Polygon)BboxUtils.createBoxPolygon(-1, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        expect2 = (Polygon)BboxUtils.createBoxPolygon(-180, -2, -76.58044236400484, 60.83962132913365);
        assertEquals(expect2, n.getGeometryN(1), "Second polygon");
        // -361 = -1 after minus 180, and -1 for maxx will cover the whole world
        n = BboxUtils.normalizeBbox(-361,-1, -76.58044236400484,60.83962132913365);
        assertEquals(1, n.getNumGeometries(), "Size correct");

        expect1 = (Polygon)BboxUtils.createBoxPolygon(-180, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");
    }
    /**
     * Very small minx so it cover whole world
     */
    @Test
    public void verifyNormalizeBboxVerySmallMinX() {
        Geometry n = BboxUtils.normalizeBbox(-650,-60,-76.58044236400484,60.83962132913365);
        assertEquals(1, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(-180, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon min 10");
    }

    /**
     * A map viewport of 112.03 to 188.90 continues only ~9 degrees past the date line.
     */
    @Test
    public void verifyNormalizeBboxViewportPastDateline() {
        double minx = 112.03097102449121;
        double maxx = 188.89962377189153;
        double miny = -50.499567507281824;
        double maxy = -4.6416246696196195;
        Geometry n = BboxUtils.normalizeBbox(minx, maxx, miny, maxy);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon eastOfAustralia = (Polygon) BboxUtils.createBoxPolygon(minx, 180, miny, maxy);
        assertEquals(eastOfAustralia, n.getGeometryN(0), "First polygon");

        Polygon pastDateline = (Polygon) BboxUtils.createBoxPolygon(-180, -171.10037622810847, miny, maxy);
        assertEquals(pastDateline, n.getGeometryN(1), "Second polygon");
    }

    @Test
    public void verifyNormalizeBboxWestOfGreenwichStaysOnePolygon() {
        Geometry n = BboxUtils.normalizeBbox(-20, -10, -5, 5);
        assertEquals(1, n.getNumGeometries(), "Size correct");
        Polygon expect = (Polygon) BboxUtils.createBoxPolygon(-20, -10, -5, 5);
        assertEquals(expect, n.getGeometryN(0), "West box");
    }

    /**
     * OBIS no-land geometry crosses the date line. The centroid of the visible clip must sit in the viewport.
     */
    @Test
    public void verifyObisCentroidInsideViewport() throws Exception {
        String json = readResourceFile("classpath:databag/097a5435-91f4-4730-90aa-ddae94afcbda.json");
        @SuppressWarnings("unchecked")
        Map<String, Object> record = GeometryUtils.getMapper().readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> summaries = (Map<String, Object>) record.get("summaries");
        Geometry noLand = GeometryUtils.readGeometry(summaries.get("proj:geometry_noland")).orElseThrow();

        double minx = 112.03097102449121;
        double maxx = 188.89962377189153;
        double miny = -50.499567507281824;
        double maxy = -4.6416246696196195;
        Geometry visible = noLand.intersection(BboxUtils.normalizeBbox(minx, maxx, miny, maxy));
        List<List<BigDecimal>> centroid = GeometryUtils.createCentroid(visible);
        Assertions.assertNotNull(centroid);
        assertEquals(1, centroid.size(), "One centroid");
        double x = centroid.get(0).get(0).doubleValue();
        double y = centroid.get(0).get(1).doubleValue();
        boolean inView = y >= miny && y <= maxy && ((x >= minx && x <= 180) || (x >= -180 && x <= maxx - 360));
        assertTrue(inView, "centroid " + centroid + " outside viewport");
    }
}
