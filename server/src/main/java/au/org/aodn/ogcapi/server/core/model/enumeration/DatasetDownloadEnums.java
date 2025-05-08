package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

/**
 * the values are used for aws batch's environment variables
 */
public class DatasetDownloadEnums {

    @Getter
    public enum Parameter {
        UUID("uuid"),
        START_DATE("start_date"),
        END_DATE("end_date"),
        MULTI_POLYGON("multi_polygon"),
        RECIPIENT("recipient"),
        TYPE("type"),
        ;

        private final String value;

        Parameter(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Type {
        SUB_SETTING("sub_setting"),
        ;
        private final String value;
        Type(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum JobDefinition {
        GENERATE_CSV_DATA_FILE("generate-csv-data-file");

        private final String value;

        JobDefinition(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum JobQueue {
        GENERATING_CSV_DATA_FILE("generate-csv-data-file");

        private final String value;

        JobQueue(String value) {
            this.value = value;
        }
    }
}
