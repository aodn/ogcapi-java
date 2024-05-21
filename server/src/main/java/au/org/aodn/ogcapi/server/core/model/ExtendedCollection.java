package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.Collection;
import au.org.aodn.ogcapi.server.core.model.enumeration.CollectionProperty;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is extended from Collection, and used to add more fields to the Collection class.
 */
@Getter
public class ExtendedCollection extends Collection {


    private final Map<CollectionProperty, Object> properties = new HashMap<>();

}
