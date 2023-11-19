package au.org.aodn.ogcapi.server.core.model.enumeration;

public enum StacUUID {
    UUID("id", "id");

    public final String searchField;
    public final String displayField;

    StacUUID(String field, String display) {
        this.searchField = field;
        this.displayField = display;
    }
}
