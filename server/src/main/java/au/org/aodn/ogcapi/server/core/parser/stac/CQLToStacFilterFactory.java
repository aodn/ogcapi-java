package au.org.aodn.ogcapi.server.core.parser.stac;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.IllegalFilterException;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.geotools.filter.identity.GmlObjectIdImpl;
import org.geotools.filter.identity.ResourceIdImpl;
import org.opengis.feature.type.Name;
import org.opengis.filter.*;
import org.opengis.filter.capability.*;
import org.opengis.filter.capability.SpatialOperator;
import org.opengis.filter.expression.*;
import org.opengis.filter.identity.*;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.opengis.filter.spatial.*;
import org.opengis.filter.temporal.*;
import org.opengis.geometry.BoundingBox;
import org.opengis.geometry.BoundingBox3D;
import org.opengis.geometry.Geometry;
import org.opengis.parameter.Parameter;
import org.opengis.util.InternationalString;
import org.xml.sax.helpers.NamespaceSupport;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Builder
@Slf4j
public class CQLToStacFilterFactory implements FilterFactory2 {

    protected CQLCrsType cqlCrsType;

    @Override
    public <T> Parameter<T> parameter(String name, Class<T> type, InternationalString title, InternationalString description, boolean required, int minOccurs, int maxOccurs, T defaultValue) {
        return new org.geotools.data.Parameter<>(name, type, title, description, required, minOccurs, maxOccurs, defaultValue, null);
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
    public PropertyName property(String s) {
        return new AttributeExpressionImpl(s);
    }

    @Override
    public PropertyName property(Name name) {
        return new AttributeExpressionImpl(name);
    }

    @Override
    public PropertyName property(String name, NamespaceSupport namespaceSupport) {
        return (namespaceSupport == null ? this.property(name) : new AttributeExpressionImpl(name, namespaceSupport));
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
    public And and(Filter filter1, Filter filter2) {
        return new AndImpl(filter1, filter2);
    }

    @Override
    public And and(List<Filter> list) {
        return new AndImpl(list);
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
        return greaterOrEqual(expression, expression1, true);
    }

    @Override
    public PropertyIsGreaterThanOrEqualTo greaterOrEqual(Expression expression, Expression expression1, boolean b) {
        return greaterOrEqual(expression, expression1, b, null);
    }

    @Override
    public PropertyIsGreaterThanOrEqualTo greaterOrEqual(Expression expression, Expression expression1, boolean b, MultiValuedFilter.MatchAction matchAction) {
        log.debug("PropertyIsGreaterThanOrEqualTo {} {}, {} {}", expression, expression1, b, matchAction);
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
    public BBOX bbox(String propertyName, double minx, double miny, double maxx, double maxy, String srs) {
        PropertyName name = this.property(propertyName);
        return this.bbox(name, minx, miny, maxx, maxy, srs);
    }

    @Override
    public BBOX bbox(Expression geometry, Expression bounds) {
        return this.bbox(geometry, bounds, MultiValuedFilter.MatchAction.ANY);
    }

    @Override
    public BBOX bbox(Expression geometry, Expression bounds, MultiValuedFilter.MatchAction matchAction) {
        return new BBoxImpl<>(geometry, bounds, matchAction);
    }

    @Override
    public BBOX bbox(String propertyName, double minx, double miny, double maxx, double maxy, String srs, MultiValuedFilter.MatchAction matchAction) {
        PropertyName name = this.property(propertyName);
        return this.bbox(name,  minx, miny, maxx, maxy, srs, matchAction);
    }

    @Override
    public BBOX bbox(Expression expression, double minx, double miny, double maxx, double maxy, String srs) {
        return this.bbox(expression, minx, miny, maxx, maxy, srs, MultiValuedFilter.MatchAction.ANY);
    }

    @Override
    public BBOX bbox(Expression expression, double minx, double miny, double maxx, double maxy, String srs, MultiValuedFilter.MatchAction matchAction) {
        return new BBoxImpl<>(expression, minx, miny, maxx, maxy, srs, matchAction);
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
    public BBOX3D bbox(Expression expression, BoundingBox3D boundingBox3D) {
        return null;
    }

    @Override
    public BBOX3D bbox(Expression expression, BoundingBox3D boundingBox3D, MultiValuedFilter.MatchAction matchAction) {
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
    public Beyond beyond(String s, Geometry geometry, double v, String s1) {
        return null;
    }

    @Override
    public Beyond beyond(String s, Geometry geometry, double v, String s1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Contains contains(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Contains contains(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Crosses crosses(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Crosses crosses(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Disjoint disjoint(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Disjoint disjoint(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public DWithin dwithin(String s, Geometry geometry, double v, String s1) {
        return null;
    }

    @Override
    public DWithin dwithin(String s, Geometry geometry, double v, String s1, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Equals equals(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Equals equals(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Intersects intersects(String s, Geometry geometry) {
        return this.intersects(s, geometry, null);
    }

    @Override
    public Intersects intersects(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        log.debug("INTERSECTS {}, {} {}", s, geometry, matchAction);
        return null;
    }

    @Override
    public Overlaps overlaps(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Overlaps overlaps(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Touches touches(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Touches touches(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
        return null;
    }

    @Override
    public Within within(String s, Geometry geometry) {
        return null;
    }

    @Override
    public Within within(String s, Geometry geometry, MultiValuedFilter.MatchAction matchAction) {
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
            return null;
        }
    }

    @Override
    public Literal literal(byte b) {
        return new LiteralExpressionImpl(b);
    }

    @Override
    public Literal literal(short i) {
        return new LiteralExpressionImpl(i);
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
    public Literal literal(float v) {
        return new LiteralExpressionImpl(v);
    }

    @Override
    public Literal literal(double v) {
        return new LiteralExpressionImpl(v);
    }

    @Override
    public Literal literal(char c) {
        return new LiteralExpressionImpl(c);
    }

    @Override
    public Literal literal(boolean b) {
        return new LiteralExpressionImpl(b);
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
    public Intersects intersects(Expression expression, Expression expression1) {
        return new IntersectsImpl<>(expression, expression1, cqlCrsType);
    }

    @Override
    public Intersects intersects(Expression expression, Expression expression1, MultiValuedFilter.MatchAction matchAction) {
        return new IntersectsImpl<>(expression, expression1, cqlCrsType);
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
