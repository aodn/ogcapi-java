package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * We do not want to expose the internal field to outsider, the CQL field in the filtler is therefore mapped to our
 * internal stac field.
 */
public enum CQLCollectionsField {
    geometry(StacSummeries.Geometry.field);

    protected final String field;

    CQLCollectionsField(String field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return this.field;
    }
}
