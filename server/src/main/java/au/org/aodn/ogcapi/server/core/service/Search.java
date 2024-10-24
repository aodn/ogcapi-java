package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.model.DatasetSearchResult;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Search {
    ElasticSearchBase.SearchResult searchCollectionWithGeometry(List<String> ids, String sortBy) throws Exception;
    ElasticSearchBase.SearchResult searchAllCollectionsWithGeometry(String sortBy) throws Exception;

    ElasticSearchBase.SearchResult searchCollections(List<String> ids, String sortBy);
    ElasticSearchBase.SearchResult searchAllCollections(String sortBy) throws Exception;
    DatasetSearchResult searchDataset(String collectionId, String startDate, String endDate) throws Exception;

    ElasticSearchBase.SearchResult searchByParameters(
            List<String> targets,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType coor
    ) throws Exception;

    BinaryResponse searchCollectionVectorTile(
            List<String> ids,
            Integer tileMatrix,
            Integer tileRow,
            Integer tileCol
    ) throws IOException;

    ResponseEntity<Map<String, ?>> getAutocompleteSuggestions(String input, String cql, CQLCrsType coor) throws Exception;
}
