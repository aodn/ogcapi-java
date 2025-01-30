package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

/**
 * the values are used for aws batch's environment variables
 */
public class DatasetDownloadEnums {

    @Getter
    public enum Condition {
        UUID("UUID"),
        START_DATE("START_DATE"),
        END_DATE("END_DATE"),
        MIN_LATITUDE("MIN_LAT"),
        MAX_LATITUDE("MAX_LAT"),
        MIN_LONGITUDE("MIN_LON"),
        MAX_LONGITUDE("MAX_LON"),
        RECIPIENT("RECIPIENT");

        private final String value;

        Condition(String value) {
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

