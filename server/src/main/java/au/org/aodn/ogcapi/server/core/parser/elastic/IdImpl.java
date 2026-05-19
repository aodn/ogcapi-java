package au.org.aodn.ogcapi.server.core.parser.elastic;

import au.org.aodn.ogcapi.server.core.model.enumeration.CQLFieldsInterface;
import au.org.aodn.ogcapi.server.core.model.enumeration.StacBasicField;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Id;
import org.opengis.filter.identity.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * ECQL parser id different, you need to pass in ID IN (id1, id2, id3), then this class will trigger to create a
 * TermsQuery for id. Other fields are fine with k=v
 * @param <T>
 */
public class IdImpl<T extends Enum<T> & CQLFieldsInterface> extends QueryHandler implements Id {

    protected Set<Identifier> identifiers = new HashSet<>();

    public IdImpl(Set<? extends Identifier> identifiers, Class<T> enumType) {
        this.identifiers.addAll(identifiers);
        this.query = TermsQuery.of(t -> t
                .field(StacBasicField.UUID.searchField)
                .terms(TermsQueryField.of(f -> f.value(identifiers.stream().map(i -> FieldValue.of(i.toString())).toList())))
        )._toQuery();
    }

    @Override
    public Set<Object> getIDs() {
        return Set.of();
    }

    @Override
    public Set<Identifier> getIdentifiers() {
        return identifiers;
    }

    @Override
    public boolean evaluate(Object o) {
        return false;
    }

    @Override
    public Object accept(FilterVisitor filterVisitor, Object o) {
        return null;
    }
}
