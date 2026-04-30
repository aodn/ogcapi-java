package au.org.aodn.ogcapi.server.core.parser.elastic;

import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.Test;
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
}
