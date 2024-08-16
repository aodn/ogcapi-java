package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * Must use lower letter for Enum here
 */
public enum CQLElasticSetting {
    score("_score"),
    page_size("page_size"),
    search_after("search_after");

    private final String setting;

    CQLElasticSetting(String setting) {
        this.setting = setting;
    }

    public String getSetting() {
        return setting;
    }
}
