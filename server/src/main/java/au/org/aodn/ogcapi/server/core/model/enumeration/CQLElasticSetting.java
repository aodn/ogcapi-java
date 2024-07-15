package au.org.aodn.ogcapi.server.core.model.enumeration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum CQLElasticSetting {
    score("_score", SupportOperation.GREATER_THAN_OR_EQUAL);

    public enum SupportOperation {
        EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL;
    };

    private final String setting;
    private final Set<SupportOperation> supportOps;

    CQLElasticSetting(String setting, SupportOperation... ops) {
        this.setting = setting;
        this.supportOps = new HashSet<>(List.of(ops));
    }

    public boolean isOperationSupported(SupportOperation o) {
        return supportOps.contains(o);
    }
}
