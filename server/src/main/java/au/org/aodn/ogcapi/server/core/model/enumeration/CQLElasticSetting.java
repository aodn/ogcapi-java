package au.org.aodn.ogcapi.server.core.model.enumeration;

/**
 * Must use lower letter for Enum here
 */
public enum CQLElasticSetting {
    score("_score");
    private final String setting;

    CQLElasticSetting(String setting) {
        this.setting = setting;
    }

    public String getSetting() {
        return setting;
    }
}
