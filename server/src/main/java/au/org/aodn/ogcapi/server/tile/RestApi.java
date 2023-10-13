package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.mapper.StacToInlineResponse2002;
import au.org.aodn.ogcapi.tile.api.*;
import au.org.aodn.ogcapi.tile.model.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;
import java.util.Optional;

/**
 * Implements the rest api of tile
 */
@RestController("TileRestApi")
public class RestApi implements CollectionsApi, MapApi, StylesApi, TileMatrixSetsApi, TilesApi {

    @Autowired
    protected RestService restService;

    @Autowired
    protected StacToInlineResponse2002 stacToInlineResponse2002;

    @Override
    public ResponseEntity<DatasetVectorGetTileSetsList200Response> collectionVectorGetTileSetsList(String collectionId, String f) {
        return restService.getTileSetsList(
                collectionId,
                OGCMediaTypeMapper.convert(f),
                stacToInlineResponse2002::convert);
    }

    /**
     * Bean always found, this effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hiden disable swagger doc
     * @param collectionId
     * @param f
     * @return
     */
    @Hidden
    @Override
    public ResponseEntity<CollectionInfo> getCollection(String collectionId, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
    /**
     * Bean always found, this effectively disable this REST point because it is common in many places and
     * should not implement it here, @Hiden disable swagger doc
     * @param datetime
     * @param bbox
     * @param limit
     * @param f
     * @return
     */
    @Hidden
    @Override
    public ResponseEntity<Collections> getCollectionsList(String datetime, GetCollectionsListBboxParameter bbox, Integer limit, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TileMatrixSet> getTileMatrixSet(String tileMatrixSetId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<DatasetVectorGetTileSetsList200Response> datasetVectorGetTileSetsList(String f) {
        return restService.getTileSetsList(
                null,
                OGCMediaTypeMapper.convert(f),
                stacToInlineResponse2002::convert);
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return CollectionsApi.super.getRequest();
    }
}
