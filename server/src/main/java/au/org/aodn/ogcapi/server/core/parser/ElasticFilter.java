package au.org.aodn.ogcapi.server.core.parser;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

public abstract class ElasticFilter {
    protected Query query;
    public Query getQuery() { return query;}
}
