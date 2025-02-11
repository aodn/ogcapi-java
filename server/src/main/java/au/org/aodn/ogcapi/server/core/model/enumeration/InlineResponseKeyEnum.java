package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum InlineResponseKeyEnum {
    MESSAGE("message"),
    STATUS("status"),
    ;
    private final String value;

    InlineResponseKeyEnum(String value) {
        this.value = value;
    }
}
