package au.org.aodn.ogcapi.server.core.model;

import au.org.aodn.ogcapi.server.core.model.enumeration.FeatureType;

/**
 * Currently only dataset as feature are needed. Please implement this
 * interface for other features if necessary.
 */
public interface IFeatureSearchResult {

    FeatureType getFeatureType();

}
