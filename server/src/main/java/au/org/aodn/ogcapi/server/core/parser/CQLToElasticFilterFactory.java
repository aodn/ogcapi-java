package au.org.aodn.ogcapi.server.core.parser;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeQuery;
import co.elastic.clients.json.JsonData;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.IllegalFilterException;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.geotools.filter.identity.GmlObjectIdImpl;
import org.geotools.filter.identity.ResourceIdImpl;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.type.Name;
import org.opengis.filter.*;
import org.opengis.filter.capability.*;
import org.opengis.filter.capability.SpatialOperator;
import org.opengis.filter.expression.*;
import org.opengis.filter.identity.*;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.BBOX3D;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Within;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;

import org.opengis.filter.temporal.*;
import org.opengis.geometry.BoundingBox;
import org.opengis.geometry.BoundingBox3D;
import org.opengis.parameter.Parameter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.InternationalString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.geojson.geom.GeometryJSON;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Here we use the CQL parser from org.geotools, but the default CQL parser do not fit our purpose. So we need to extends
 * FilterFactoryImpl and add the needed items for our purpose.
 *
 * Not thread-safe, please create factory each time before compile
 *
 * TODO: Need to implement all functions later, right now only a small amount of functions is done.
 */
public class CQLToElasticFilterFactory<T extends Enum<T>> implements FilterFactory2 {

    protected Logger logger = LoggerFactory.getLogger(CQLToElasticFilterFactory.class);

    protected Class<T> enumType;

    protected CQLCrsType cqlCoorSystem;

    public CQLToElasticFilterFactory(CQLCrsType cqlCoorSystem, Class<T> tClass) {
        this.cqlCoorSystem = cqlCoorSystem;
        this.enumType = tClass;
    }
    /**
     * Convert the WKT format from the cql to GeoJson use by Elastic search
     * @param literalExpression
     * @return
     * @throws ParseException
     * @throws IOException
     */
    protected String convertToGeoJson(LiteralExpressionImpl literalExpression) throws ParseException, IOException, FactoryException, TransformException {
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
        Query geoShapeQuery = null;

        if(geometry1 instanceof AttributeExpressionImpl attribute && geometry2 instanceof LiteralExpressionImpl literal) {
            try {
                String geojson = convertToGeoJson(literal);

                // Create elastic query here
                geoShapeQuery = new GeoShapeQuery.Builder()
                        .field(Enum.valueOf(enumType,attribute.getPropertyName()).toString())
                        .shape(builder -> builder
                                .relation(GeoShapeRelation.Intersects)
                                .shape(JsonData.from(new StringReader(geojson))))
                        .build()
                        ._toQuery();
            }
            catch (ParseException | IOException e) {
                return new IntersectsImpl(geometry1, geometry2, null);
            }
            catch (FactoryException | TransformException e) {
                return new IntersectsImpl(geometry1, geometry2, null);
            }
        }
        return new IntersectsImpl(geometry1, geometry2, geoShapeQuery);
    }

    @Override
    public <T> Parameter<T> parameter(String name, Class<T> type, InternationalString title, InternationalString description, boolean required, int minOccurs, int maxOccurs, T defaultValue) {
        return new org.geotools.data.Parameter(name, type, title, description, required, minOccurs, maxOccurs, defaultValue, (Map)null);
    }

    @Override
    public FunctionName functionName(String s, int i, List<String> list) {
        return null;
    }

    @Override
    public FunctionName functionName(Name name, int i, List<String> list) {
        return null;
    }

    @Override
    public FunctionName functionName(String s, List<Parameter<?>> list, Parameter<?> parameter) {
        return null;
    }

    @Override
    public FunctionName functionName(Name name, List<Parameter<?>> list, Parameter<?> parameter) {
        return null;
    }

    @Override
    public Id id(FeatureId... featureIds) {
        return null;
    }

    @Override
    public PropertyName property(Name name) {
        return new AttributeExpressionImpl(name);
    }

    @Override
    public PropertyName property(String name, NamespaceSupport namespaceContext) {
        return (namespaceContext == null ? this.property(name) : new AttributeExpressionImpl(name, namespaceContext));
    }

    @Override
    public FeatureId featureId(String id) {
        return new FeatureIdImpl(id);
    }

    @Override
    public FeatureId featureId(String fid, String featureVersion) {
        return new FeatureIdVersionedImpl(fid, featureVersion);
    }

    @Override
    public GmlObjectId gmlObjectId(String id) {
        return new GmlObjectIdImpl(id);
    }

    @Override
    public ResourceId resourceId(String fid, String featureVersion, Version version) {
        return new ResourceIdImpl(fid, featureVersion, version);
    }

    @Override
    public ResourceId resourceId(String fid, Date startTime, Date endTime) {
        return new ResourceIdImpl(fid, startTime, endTime);
    }

    @Override
    public And and(Filter filter, Filter filter1) {
        return null;
    }

    @Override
    public And and(List<Filter> list) {
        return null;
    }

    @Override
    public Or or(Filter filter, Filter filter1) {
        return null;
    }

    @Override
    public Or or(List<Filter> list) {
        return null;
    }

    @Override
    public Not not(Filter filter) {
        return null;
    }

    @Override
    public Id id(Set<? extends Identifier> set) {
        return null;
    }

    @Override
    public PropertyName property(String name) {
        return new AttributeExpressionImpl(name);
    }

    @Override
    public PropertyIsBetween between(Expression expression, Expression expression1, Expression expression2) {
        return null;
    }

    @Override
    public PropertyIsBetween between(Expression expression, Expression expression1, Expression expression2, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsEqualTo equals(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsEqualTo equal(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsEqualTo equal(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsNotEqualTo notEqual(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsNotEqualTo notEqual(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsNotEqualTo notEqual(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsGreaterThan greater(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsGreaterThan greater(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsGreaterThan greater(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsGreaterThanOrEqualTo greaterOrEqual(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsGreaterThanOrEqualTo greaterOrEqual(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsGreaterThanOrEqualTo greaterOrEqual(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsLessThan less(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsLessThan less(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsLessThan less(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsLessThanOrEqualTo lessOrEqual(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public PropertyIsLessThanOrEqualTo lessOrEqual(Expression expression, Expression expression1, boolean b) {
        return null;
    }

    @Override
    public PropertyIsLessThanOrEqualTo lessOrEqual(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsLike like(Expression expression, String s) {
        return null;
    }

    @Override
    public PropertyIsLike like(Expression expression, String s, String s1, String s2, String s3) {
        return null;
    }

    @Override
    public PropertyIsLike like(Expression expression, String s, String s1, String s2, String s3, boolean b) {
        return null;
    }

    @Override
    public PropertyIsLike like(Expression expression, String s, String s1, String s2, String s3, boolean b, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public PropertyIsNull isNull(Expression expression) {
        return null;
    }

    @Override
    public PropertyIsNil isNil(Expression expression, Object o) {
        return null;
    }

    @Override
    public BBOX bbox(String s, double v, double v1, double v2, double v3, String s1) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public BBOX3D bbox(String s, BoundingBox3D boundingBox3D) {
        return null;
    }

    @Override
    public BBOX3D bbox(String s, BoundingBox3D boundingBox3D, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public BBOX bbox(String s, double v, double v1, double v2, double v3, String s1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Beyond beyond(String s, org.opengis.geometry.Geometry geometry, double v, String s1) {
        return null;
    }

    @Override
    public Beyond beyond(String s, org.opengis.geometry.Geometry geometry, double v, String s1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Contains contains(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Contains contains(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Crosses crosses(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Crosses crosses(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Disjoint disjoint(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Disjoint disjoint(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public DWithin dwithin(String s, org.opengis.geometry.Geometry geometry, double v, String s1) {
        return null;
    }

    @Override
    public DWithin dwithin(String s, org.opengis.geometry.Geometry geometry, double v, String s1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Equals equals(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Equals equals(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Intersects intersects(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Intersects intersects(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Overlaps overlaps(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Overlaps overlaps(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Touches touches(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Touches touches(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Within within(String s, org.opengis.geometry.Geometry geometry) {
        return null;
    }

    @Override
    public Within within(String s, org.opengis.geometry.Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public After after(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public After after(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public AnyInteracts anyInteracts(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public AnyInteracts anyInteracts(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Before before(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Before before(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Begins begins(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Begins begins(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public BegunBy begunBy(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public BegunBy begunBy(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public During during(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public During during(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public EndedBy endedBy(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public EndedBy endedBy(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Ends ends(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Ends ends(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Meets meets(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Meets meets(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public MetBy metBy(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public MetBy metBy(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public OverlappedBy overlappedBy(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public OverlappedBy overlappedBy(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public TOverlaps toverlaps(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public TOverlaps toverlaps(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public TContains tcontains(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public TContains tcontains(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public TEquals tequals(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public TEquals tequals(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Add add(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Divide divide(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Multiply multiply(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Subtract subtract(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Function function(String s, Expression... expressions) {
        return null;
    }

    @Override
    public Function function(Name name, Expression... expressions) {
        return null;
    }

    @Override
    public Literal literal(Object obj) {
        try {
            return new LiteralExpressionImpl(obj);
        }
        catch (IllegalFilterException var3) {
            (new IllegalArgumentException()).initCause(var3);
            return null;
        }
    }

    @Override
    public Literal literal(byte b) {
        return new LiteralExpressionImpl(b);
    }

    @Override
    public Literal literal(short s) {
        return new LiteralExpressionImpl(s);
    }

    @Override
    public Literal literal(int i) {
        return new LiteralExpressionImpl(i);
    }

    @Override
    public Literal literal(long l) {
        return new LiteralExpressionImpl(l);
    }

    @Override
    public Literal literal(float f) {
        return new LiteralExpressionImpl((double)f);
    }

    @Override
    public Literal literal(double d) {
        return new LiteralExpressionImpl(d);
    }

    @Override
    public Literal literal(char c) {
        return new LiteralExpressionImpl(c);
    }

    @Override
    public Literal literal(boolean b) {
        return b ? new LiteralExpressionImpl(Boolean.TRUE) : new LiteralExpressionImpl(Boolean.FALSE);
    }

    @Override
    public SortBy sort(String s, SortOrder sortOrder) {
        return null;
    }

    @Override
    public Operator operator(String s) {
        return null;
    }

    @Override
    public SpatialOperator spatialOperator(String s, GeometryOperand... geometryOperands) {
        return null;
    }

    @Override
    public TemporalOperator temporalOperator(String s) {
        return null;
    }

    @Override
    public FunctionName functionName(String s, int i) {
        return null;
    }

    @Override
    public FunctionName functionName(Name name, int i) {
        return null;
    }

    @Override
    public Functions functions(FunctionName... functionNames) {
        return null;
    }

    @Override
    public SpatialOperators spatialOperators(SpatialOperator... spatialOperators) {
        return null;
    }

    @Override
    public ComparisonOperators comparisonOperators(Operator... operators) {
        return null;
    }

    @Override
    public ArithmeticOperators arithmeticOperators(boolean b, Functions functions) {
        return null;
    }

    @Override
    public ScalarCapabilities scalarCapabilities(ComparisonOperators comparisonOperators, ArithmeticOperators arithmeticOperators, boolean b) {
        return null;
    }

    @Override
    public SpatialCapabilities spatialCapabilities(GeometryOperand[] geometryOperands, SpatialOperators spatialOperators) {
        return null;
    }

    @Override
    public IdCapabilities idCapabilities(boolean b, boolean b1) {
        return null;
    }

    @Override
    public TemporalCapabilities temporalCapabilities(TemporalOperator... temporalOperators) {
        return null;
    }

    @Override
    public FilterCapabilities capabilities(String s, ScalarCapabilities scalarCapabilities, SpatialCapabilities spatialCapabilities, IdCapabilities idCapabilities) {
        return null;
    }

    @Override
    public FilterCapabilities capabilities(String s, ScalarCapabilities scalarCapabilities, SpatialCapabilities spatialCapabilities, IdCapabilities idCapabilities, TemporalCapabilities temporalCapabilities) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, double v, double v1, double v2, double v3, String s) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, double v, double v1, double v2, double v3, String s, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public BBOX3D bbox(Expression expression, BoundingBox3D boundingBox3D) {
        return null;
    }

    @Override
    public BBOX3D bbox(Expression expression, BoundingBox3D boundingBox3D, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, BoundingBox boundingBox) {
        return null;
    }

    @Override
    public BBOX bbox(Expression expression, BoundingBox boundingBox, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Beyond beyond(Expression expression, Expression expression1, double v, String s) {
        return null;
    }

    @Override
    public Beyond beyond(Expression expression, Expression expression1, double v, String s, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Contains contains(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Contains contains(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Crosses crosses(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Crosses crosses(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Disjoint disjoint(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Disjoint disjoint(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public DWithin dwithin(Expression expression, Expression expression1, double v, String s) {
        return null;
    }

    @Override
    public DWithin dwithin(Expression expression, Expression expression1, double v, String s, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Equals equal(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Equals equal(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Intersects intersects(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Overlaps overlaps(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Overlaps overlaps(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Touches touches(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Touches touches(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Within within(Expression expression, Expression expression1) {
        return null;
    }

    @Override
    public Within within(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }
}
