package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum ErrorCode {
    ELASTICSEARCH_UNAVAILABLE("DEGRADED", "E1000", "Dependency unavailable"),
    MISSING_CO_CORE_INDEX("DEGRADED", "E1001","Missing cloud optimized index"),
    MISSING_VOCAB_INDEX("DEGRADED", "E1002","Missing vocab index"),
    MISSING_CORE_INDEX("DEGRADED", "E1003","Missing core index");

    private final String status;
    private final String code;
    private final String message;

    ErrorCode(String status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
