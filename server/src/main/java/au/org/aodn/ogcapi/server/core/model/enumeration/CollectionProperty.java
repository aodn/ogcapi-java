package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

@Getter
public enum CollectionProperty {
    STATUS("status"),
    CREDITS("credits"),
    CONTACTS("contacts"),
    ;



    private final String value;

    CollectionProperty(String value) {
        this.value = value;
    }

}
