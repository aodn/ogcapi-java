package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import lombok.extern.slf4j.Slf4j;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class IBoundaryFunctionTest {

    @Test
    public void testIBoundaryFunction() {
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

        Expression func = ff.function("IBOUNDARY",
                ff.literal("ACA"),
                ff.literal("1"));

        Object geom = func.evaluate(null);
        assertNotNull(geom);
        assertInstanceOf(Geometry.class, geom);
    }

    @Test
    public void testIBoundaryWithCQL() throws Exception {
        // Parse full CQL2 string
        String cql = "INTERSECTS(geometry, IBOUNDARY('ACA', '1'))";
        CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(CQLCrsType.EPSG4326, CQLFields.class);
        Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);

        String expected = BaseTestClass.readResourceFile("classpath:canned/elastic_query1.json");
        // The string is not in json format, need to report the Query: part
        String actual = ((IntersectsImpl<?>)filter).query.toString().replaceAll("Query:", "");
        assertTrue(BaseTestClass.isJsonEqual(expected, actual));
    }
}
