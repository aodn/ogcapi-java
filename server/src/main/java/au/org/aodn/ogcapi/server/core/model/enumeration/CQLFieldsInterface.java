package au.org.aodn.ogcapi.server.core.model.enumeration;

import co.elastic.clients.elasticsearch._types
        .SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.TopLeftBottomRightGeoBounds;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;

import java.util.List;
import java.util.function.Function;

public interface CQLFieldsInterface {
    List<String> getDisplayField();
    Function<SortOrder, ObjectBuilder<SortOptions>> getSortBuilder();
    Query getPropertyEqualToQuery(String literal);
    Query getIntersectsQuery(String literal);
    Query getIsNullQuery();
    Query getLikeQuery(String literal);
    Query getPropertyGreaterThanOrEqualsToQuery(String literal);
    Query getBoundingBoxQuery(TopLeftBottomRightGeoBounds tlbr);

    /**
     * Membership query for {@code property IN (v1, v2, ...)}. Null means the caller should
     * keep the default OR of equals clauses.
     */
    Query getPropertyInQuery(List<String> literals);
}
