package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum ProcessIdEnum {
    DOWNLOAD_DATASET("download"),
    ;

    private final String value;

    ProcessIdEnum(String value) {
        this.value = value;
    }
}
