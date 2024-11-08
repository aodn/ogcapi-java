package au.org.aodn.ogcapi.server.core.parser.stac;

import org.opengis.filter.And;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AndImpl implements And {

    protected List<Filter> filters= new ArrayList<>();

    public AndImpl(Filter filter1, Filter filter2) {
        if(filter1 != null) {
            filters.add(filter1);
        }

        if(filter2 != null) {
            filters.add(filter2);
        }
    }

    public AndImpl(List<Filter> fs) {
        filters.addAll(fs.stream().filter(Objects::nonNull).toList());
    }

    @Override
    public List<Filter> getChildren() {
        return filters;
    }

    @Override
    public boolean evaluate(Object o) {
        return true;
    }

    @Override
    public Object accept(FilterVisitor visitor, Object extraData) {
        // Do nothing for now.
        return visitor.visit(this, extraData);
    }
}
