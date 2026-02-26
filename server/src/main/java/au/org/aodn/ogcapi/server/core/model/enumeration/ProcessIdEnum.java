package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum ProcessIdEnum {
    DOWNLOAD_DATASET("download"),
    DOWNLOAD_WFS_SSE("downloadWfs"),
    DOWNLOAD_WFS_SIZE("downloadWfsSize"),
    UNKNOWN("");

    private final String value;

    ProcessIdEnum(String value) {
        this.value = value;
    }

    public static ProcessIdEnum fromString(String text) {
        for (ProcessIdEnum e : values()) {
            if (e.value.equalsIgnoreCase(text)) {
                return e;
            }
        }
        return UNKNOWN;
    }

}
