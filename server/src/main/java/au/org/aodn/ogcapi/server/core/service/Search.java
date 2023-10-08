package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import java.util.List;

public interface Search {
    List<StacCollectionModel> searchCollectionWithGeometry(String id) throws Exception;
    List<StacCollectionModel> searchAllCollectionsWithGeometry() throws Exception;
    List<StacCollectionModel> searchAllCollections() throws Exception;
    List<StacCollectionModel> searchByTitleDescKeywords(List<String> targets) throws Exception;
}
