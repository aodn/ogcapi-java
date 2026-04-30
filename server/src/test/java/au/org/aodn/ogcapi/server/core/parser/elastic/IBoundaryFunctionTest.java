package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFields;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.commons.CompilerUtil;
import org.geotools.filter.text.commons.Language;
import org.junit.jupiter.api.Test;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;

import static org.junit.jupiter.api.Assertions.*;

public class IBoundaryFunctionTest {

    @Test
    public void testIBoundaryFunction() {
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

        Expression func = ff.function("IBOUNDARY",
                ff.literal("CORAL_ATLAS"),
                ff.literal("1"));

        Object geom = func.evaluate(null);
        assertNotNull(geom);
        assertInstanceOf(String.class, geom);
    }

    @Test
    public void testIBoundaryWithCQL() throws Exception {
        // Parse full CQL2 string
        String cql = "INTERSECTS(geom, IBOUNDARY('xxx', 'polygon'))";
        CQLToElasticFilterFactory<CQLFields> factory = new CQLToElasticFilterFactory<>(CQLCrsType.EPSG4326, CQLFields.class);
        Filter filter = CompilerUtil.parseFilter(Language.ECQL, cql, factory);
    }
}
