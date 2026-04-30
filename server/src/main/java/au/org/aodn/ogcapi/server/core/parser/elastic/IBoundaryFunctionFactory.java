package au.org.aodn.ogcapi.server.core.parser.elastic;

import org.geotools.feature.NameImpl;
import org.geotools.filter.FunctionFactory;
import org.opengis.feature.type.Name;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;

import java.util.List;

public class IBoundaryFunctionFactory implements FunctionFactory {

    @Override
    public List<FunctionName> getFunctionNames() {
        return List.of(IBoundaryFunction.NAME);
    }

    @Override
    public Function function(String name, List<Expression> args, Literal fallback) {
        return function(new NameImpl(name), args, fallback);
    }

    @Override
    public Function function(Name name, List<Expression> args, Literal fallback) {
        if (IBoundaryFunction.NAME.getFunctionName().equals(name)) {
            return new IBoundaryFunction(args, fallback);
        }
        return null;
    }
}