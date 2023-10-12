package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 */
public enum CQLFieldMapper {
    geometry("summaries.proj:geometry");

    public final String field;

    CQLFieldMapper(String field) {
        this.field = field;
    }
}
