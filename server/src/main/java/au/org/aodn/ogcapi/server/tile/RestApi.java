package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.mapper.BinaryResponseToBytes;
import au.org.aodn.ogcapi.server.core.mapper.StacToTileSetWmWGS84Q;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.mapper.StacToInlineResponse2002;
import au.org.aodn.ogcapi.tile.api.*;
import au.org.aodn.ogcapi.tile.model.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Implements the rest api of tile
 */
@RestController("TileRestApi")
public class RestApi implements CollectionsApi, MapApi, StylesApi, TileMatrixSetsApi, TilesApi {

    @Autowired
    protected RestService restService;

    @Autowired
    protected StacToInlineResponse2002 stacToInlineResponse2002;

    @Autowired
    protected StacToTileSetWmWGS84Q stacToTileSet;

    @Autowired
    protected BinaryResponseToBytes binaryResponseToByte;

    @Override
    public ResponseEntity<String> collectionCoverageGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionCoverageGetTileSet(String collectionId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionCoverageGetTileSetsList(String collectionId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionMapGetTileSet(String collectionId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionMapGetTileSetsList(String collectionId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionStyleMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, String styleId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionStyleMapGetTileSet(String collectionId, String styleId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionStyleMapGetTileSetsList(String collectionId, String styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionStyleVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, String styleId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionStyleVectorGetTileSet(String collectionId, String styleId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionStyleVectorGetTileSetsList(String collectionId, String styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionVectorGetTileSet(String collectionId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionVectorGetTileSetsList(String collectionId, String f) {
        return restService.getTileSetsListOfCollection(
                List.of(collectionId),
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
    public ResponseEntity<Collections> getCollectionsList(String datetime, List bbox, Integer limit, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<String> datasetMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TileSet> datasetMapGetTileSet(TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetMapGetTileSetsList(String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }


    @Override
    public ResponseEntity<String> datasetStyleMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, Styles styleId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TileSet> datasetStyleMapGetTileSet(Styles styleId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetStyleMapGetTileSetsList(Styles styleId, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<String> datasetStyleVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, Styles styleId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TileSet> datasetStyleVectorGetTileSet(Styles styleId, TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }


    @Override
    public ResponseEntity<InlineResponse2002> datasetStyleVectorGetTileSetsList(Styles styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileMatrixSet> getTileMatrixSet(TileMatrixSets tileMatrixSetId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2001> getTileMatrixSetsList(String f) {
        return null;
    }
    /**
     * Return mvt from search instance, type should be protocol buffer
     * @param tileMatrix
     * @param tileRow
     * @param tileCol
     * @param tileMatrixSetId
     * @param datetime
     * @param collections
     * @param subset
     * @param crs
     * @param subsetCrs
     * @param f
     * @return
     */
    @CrossOrigin(origins = "*") //TODO: Just good for testing
    @Override
    public ResponseEntity<?> datasetVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return restService.getVectorTileOfCollection(
                tileMatrixSetId,
                collections,
                Integer.valueOf(tileMatrix),
                tileRow,
                tileCol,
                binaryResponseToByte::convert);
    }

    @Override
    public ResponseEntity<TileSet> datasetVectorGetTileSet(TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        switch(tileMatrixSetId) {
            case WEBMERCATORQUAD: {
                //TODO: The return set seems not correct more study needed.
                return restService.getTileSetsListOfCollection(
                        collections,
                        OGCMediaTypeMapper.convert(f),
                        stacToTileSet::convert);
            }
            default: {
                // We support WEBMERCATORQUAD at the moment, so if it isn't return empty set.
                return ResponseEntity.status(HttpStatus.OK).body(new TileSet());
            }
        }
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetVectorGetTileSetsList(String f) {
        return restService.getTileSetsListOfCollection(
                null,
                OGCMediaTypeMapper.convert(f),
                stacToInlineResponse2002::convert);
    }
}
