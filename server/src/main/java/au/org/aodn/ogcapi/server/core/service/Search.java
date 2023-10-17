package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import co.elastic.clients.transport.endpoints.BinaryResponse;

import java.io.IOException;
import java.util.List;

public interface Search {
    List<StacCollectionModel> searchCollectionWithGeometry(List<String> id) throws Exception;
    List<StacCollectionModel> searchAllCollectionsWithGeometry() throws Exception;
    List<StacCollectionModel> searchAllCollections() throws Exception;
    List<StacCollectionModel> searchByParameters(List<String> targets, String filter) throws Exception;
    BinaryResponse searchCollectionVectorTile(List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol) throws IOException;
}
