package au.org.aodn.ogcapi.server.core.model.enumeration;

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
    geometry("geometry")
    ;

    private final String value;

    CollectionProperty(String value) {
        this.value = value;
    }

}
