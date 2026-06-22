package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.enumeration.CQLCrsType;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Search {
    ElasticSearchBase.SearchResult<StacCollectionModel> searchCollectionWithGeometry(List<String> ids, String sortBy) throws Exception;
    ElasticSearchBase.SearchResult<StacCollectionModel> searchAllCollectionsWithGeometry(String sortBy) throws Exception;

    ElasticSearchBase.SearchResult<StacCollectionModel> searchCollections(String id);
    ElasticSearchBase.SearchResult<StacCollectionModel> searchCollections(List<String> ids, String sortBy);
    ElasticSearchBase.SearchResult<StacCollectionModel> searchAllCollections(String sortBy) throws Exception;
    ElasticSearchBase.SearchResult<FeatureGeoJSON>searchFeatureSummary(String collectionId, List<String> properties, String filter) throws Exception;

    ElasticSearchBase.SearchResult<StacCollectionModel> searchByParameters(
            List<String> targets,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType coor
    ) throws Exception;

    JsonNode explainByParameters(
            List<String> targets,
            String filter,
            List<String> properties,
            String sortBy,
            CQLCrsType coor
    ) throws Exception;

    JsonNode explainByUuid(
            String uuid,
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
