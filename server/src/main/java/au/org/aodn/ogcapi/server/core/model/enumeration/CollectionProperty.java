package au.org.aodn.ogcapi.server.core.model.enumeration;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Please use lower case here so that json output attribute name is lower case
 */
@Getter
public enum CollectionProperty {
    status("status"),
    credits("credits"),
    contacts("contacts"),
    themes("themes"),
    geometry("geometry"),
    temporal("temporal"),
    citation("citation"),
    statement("statement"),
    license("license"),
    creation("creation"),
    revision("revision"),
    centroid("centroid"),
    pace("pace"),
    datasetGroup("dataset_group"),
    aiDescription("ai:description"),
    aiUpdateFrequency("ai:update_frequency"),
    scope("scope"),
    parameterVocabs("parameter_vocabs");

    private final String value;

    CollectionProperty(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
