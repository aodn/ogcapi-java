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
        FIELDS("fields"),
        LAYER_NAME("layer_name"),
        COLLECTION_TITLE("collection_title"),
        FULL_METADATA_LINK("full_metadata_link"),
        SUGGESTED_CITATION("suggested_citation"),
        ;

        private final String value;

        Parameter(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Type {
        SUB_SETTING("sub-setting"),
        ;
        private final String value;
        Type(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum JobDefinition {
        GENERATE_CSV_DATA_FILE("generate-csv-data-file-dev");

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
