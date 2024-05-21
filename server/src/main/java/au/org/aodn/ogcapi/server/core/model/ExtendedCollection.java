package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.features.model.Collection;
import lombok.Getter;
import lombok.Setter;


/**
 * This class is extended from Collection, and used to add more fields to the Collection class.
 */
@Setter
@Getter
public class ExtendedCollection extends Collection {

    private SummariesModel summaries;

}
