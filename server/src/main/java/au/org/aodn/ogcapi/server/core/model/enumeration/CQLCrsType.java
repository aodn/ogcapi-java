package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.Arrays;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum CQLCrsType {
    EPSG4326("EPSG:4326", 4326, "https://epsg.io/4326"),
    EPSG3857("EPSG:3857", 3785, "https://epsg.io/3857"),
    UNKNOWN(null, null, null);

    protected static Logger logger = LoggerFactory.getLogger(CQLCrsType.class);

    public final String code;
    public final String url;
    public final Integer srid;

    CQLCrsType(String code, Integer srid, String url) {
        this.code = code;
        this.url = url;
        this.srid = srid;
    }

    public static CQLCrsType convertFromUrl(String url) {
        return Arrays.stream(CQLCrsType.values())
                .filter(f -> f.url.equals(url))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static Geometry transformGeometry(Geometry geometry, CQLCrsType source, CQLCrsType target) throws FactoryException, TransformException {

        GeometryFactory factory = JTSFactoryFinder.getGeometryFactory();

        CoordinateReferenceSystem sourceCRS = CRS.decode(source.code);
        CoordinateReferenceSystem targetCRS = CRS.decode(target.code);

        MathTransform mTrans = CRS.findMathTransform(sourceCRS, targetCRS, true);
        return JTS.transform(geometry, mTrans);
    }
}
