package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum DateFormatEnum {

    YYYY_MM_DD_HYPHEN("yyyy-MM-dd"),
    DD_MM_YYYY_HYPHEN("dd-MM-yyyy"),
    YYYY_MM_DD_SLASH("yyyy/MM/dd"),
    DD_MM_YYYY_SLASH("dd/MM/yyyy"),
    MM_YYYY_HYPHEN("MM-yyyy"),
    ;

    private final String value;

    DateFormatEnum(String value) {
        this.value = value;
    }

}
