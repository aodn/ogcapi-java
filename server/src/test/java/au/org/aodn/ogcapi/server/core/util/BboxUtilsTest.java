package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BboxUtilsTest {
    /**
     * This test have bbox start from -4.6 -> 0 -> 180 -> -180 -> -76.5
     */
    @Test
    public void verifyNormalizeBbox1() {
        Geometry n = BboxUtils.normalizeBbox(-4.614697916267209,314.17320304524225,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(-4.614697916267209, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        Polygon expect3 = (Polygon)BboxUtils.createBoxPolygon(-180, -134.17320304524225, -76.58044236400484, 60.83962132913365);
        assertEquals(expect3, n.getGeometryN(1), "Second polygon");
    }
    /**
     * This test have bbox start from 10 -> 180 -> -180 -> -76.5
     */
    @Test
    public void verifyNormalizeBbox2() {
        Geometry n = BboxUtils.normalizeBbox(10,314.17320304524225,-76.58044236400484,60.83962132913365);
        assertEquals(2, n.getNumGeometries(), "Size correct");

        Polygon expect1 = (Polygon)BboxUtils.createBoxPolygon(10, 180, -76.58044236400484, 60.83962132913365);
        assertEquals(expect1, n.getGeometryN(0), "First polygon");

        Polygon expect2 = (Polygon)BboxUtils.createBoxPolygon(-180, -134.17320304524225, -76.58044236400484, 60.83962132913365);
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
}
