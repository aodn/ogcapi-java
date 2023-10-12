package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.spatial.IntersectsImpl;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.filter.MultiValuedFilter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.spatial.Intersects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.geotools.geojson.geom.GeometryJSON;

/**
 * Here we use the CQL parser from org.geotools, but the default CQL parser do not fit our purpose. So we need to extends
 * FilterFactoryImpl and add the needed items for our purpose.
 *
 * Not thread-safe, please create factory each time before compile
 *
 * TODO: Need to implement all functions later, right now only a small amount of functions is done.
 */
public class CQLToElasticFilterFactory<T extends Enum<T>> extends FilterFactoryImpl {

    protected Logger logger = LoggerFactory.getLogger(CQLToElasticFilterFactory.class);

    protected List<Query> queries = new ArrayList<>();

    protected Class<T> enumType;

    public CQLToElasticFilterFactory(Class<T> tClass) {
        this.enumType = tClass;
    }

    public List<Query> getQueries() {
        return queries;
    }
    /**
     * Convert the WKT format from the cql to GeoJson use by Elastic search
     * @param literalExpression
     * @return
     * @throws ParseException
     * @throws IOException
     */
    protected String convertToGeoJson(LiteralExpressionImpl literalExpression) throws ParseException, IOException {
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry geo = reader.read(literalExpression.toString());

        try(StringWriter writer = new StringWriter()) {
            GeometryJSON geometryJson = new GeometryJSON();
            geometryJson.write(geo, writer);

            String r = writer.toString();
            logger.debug("Converted to GeoJson {}", r);
            return r;
        }
    }
    /**
     * Expect statement like this:
     * INTERSECTS(geometry,
     *  POLYGON(
     *      (1379213.867288 3610774.164192,1379233.837424 3610769.696029,1379246.149564 3610812.389132,1379226.494235 3610816.884823,1379213.867288 3610774.164192)
     *  )
     * )
     *
     * @param geometry1 - The attribute value, it will be mapped by the type T enum to the real field name in Elastic
     * @param geometry2 - The Polygon and will convert to GeoJson
     * @return
     */
    @Override
    public Intersects intersects(Expression geometry1, Expression geometry2) {
        logger.debug("INTERSECTS {}, {}", geometry1, geometry2);
        if(geometry1 instanceof AttributeExpressionImpl attribute && geometry2 instanceof LiteralExpressionImpl literal) {
            try {
                String geojson = convertToGeoJson(literal);

                // Create elastic query here
                Query geoShapeQuery = new GeoShapeQuery.Builder()
                        .field(Enum.valueOf(enumType,attribute.getPropertyName()).toString())
                        .shape(builder -> builder
                                .relation(GeoShapeRelation.Intersects)
                                .shape(JsonData.from(new StringReader(geojson))))
                        .build()
                        ._toQuery();

                queries.add(geoShapeQuery);

            } catch (ParseException | IOException e) {
                e.printStackTrace();
            }

        }
        return new IntersectsImpl(geometry1, geometry2);
    }

    @Override
    public Intersects intersects(Expression geometry1, Expression geometry2, MultiValuedFilter.MatchAction matchAction) {
        // TODO: implement
        logger.debug("INTERSECTS {}, {}, {}", geometry1, geometry2, matchAction);
        return new IntersectsImpl(geometry1, geometry2, matchAction);
    }

    @Override
    public Intersects intersects(String propertyName, org.opengis.geometry.Geometry geometry) {
        // TODO: implement
        // Add the Elastic query here then we continue what the default impl do.
        logger.debug("INTERSECTS {}, {}", propertyName, geometry);
        return super.intersects(propertyName, geometry);
    }

    @Override
    public Intersects intersects(String propertyName, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        // TODO: implement
        logger.debug("INTERSECTS {}, {}, {}", propertyName, geometry, matchAction);
        return super.intersects(propertyName,geometry,matchAction);
    }
}
