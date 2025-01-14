package au.org.aodn.ogcapi.processes.processes;

import lombok.Getter;

@Getter
public enum ProcessIdEnum {
    GENERATE_CDF("generate-cdf"),

    ;

    private final String value;

    ProcessIdEnum(String value) {
        this.value = value;
    }

}
