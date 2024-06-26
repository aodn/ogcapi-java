package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Search {
    List<StacCollectionModel> searchCollectionWithGeometry(List<String> ids) throws Exception;
    List<StacCollectionModel> searchAllCollectionsWithGeometry() throws Exception;

    List<StacCollectionModel> searchCollections(List<String> ids) throws Exception;
    List<StacCollectionModel> searchAllCollections() throws Exception;

    List<StacCollectionModel> searchByParameters(List<String> targets, String filter, CQLCrsType coor, List<String> properties) throws Exception;

    BinaryResponse searchCollectionVectorTile(List<String> ids, Integer tileMatrix, Integer tileRow, Integer tileCol) throws IOException;

    ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws Exception;
}
