package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.Getter;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public abstract class QueryHandler {

    @Getter
    protected Query query;

    @Getter
    protected List<CQLException> errors = new ArrayList<>();

    protected final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    protected Logger logger = LoggerFactory.getLogger(QueryHandler.class);

    public void addErrors(CQLException... e) { this.errors.addAll(List.of(e)); }

    public void addErrors(List<CQLException> e) { this.errors.addAll(e); }
    /**
     * Convert the WKT format from the cql to GeoJson use by Elastic search
     * @param literalExpression
     * @return
     * @throws ParseException
     * @throws IOException
     */
    protected String convertToGeoJson(LiteralExpressionImpl literalExpression, CQLCrsType cqlCoorSystem) throws ParseException, IOException, FactoryException, TransformException {
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry geo = reader.read(literalExpression.toString());

        try(StringWriter writer = new StringWriter()) {
            GeometryJSON geometryJson = new GeometryJSON();
            Geometry t = CQLCrsType.transformGeometry(geo, cqlCoorSystem, CQLCrsType.EPSG4326);
            geometryJson.write(t, writer);

            String r = writer.toString();
            logger.debug("Converted to GeoJson {}", r);
            return r;
        }
    }
}
