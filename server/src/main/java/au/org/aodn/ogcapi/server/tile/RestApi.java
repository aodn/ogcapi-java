package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.tile.api.CollectionsApi;
import au.org.aodn.ogcapi.tile.api.MapApi;
import au.org.aodn.ogcapi.tile.api.StylesApi;
import au.org.aodn.ogcapi.tile.api.TileMatrixSetsApi;
import au.org.aodn.ogcapi.tile.model.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements the rest api of tile
 */
@RestController("TileRestApi")
public class RestApi implements CollectionsApi, MapApi, StylesApi, TileMatrixSetsApi {
    @Override
    public ResponseEntity<String> collectionCoverageGetTile(String tileMatrix, Integer tileRow, Integer tileCol, CoverageCollections collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<CoverageCollections> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionCoverageGetTileSet(CoverageCollections collectionId, TileMatrixSets tileMatrixSetId, List<CoverageCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionCoverageGetTileSetsList(CoverageCollections collectionId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, AllCollections collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<AllCollections> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionMapGetTileSet(AllCollections collectionId, TileMatrixSets tileMatrixSetId, List<AllCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionMapGetTileSetsList(AllCollections collectionId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionStyleMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, AllCollections collectionId, String styleId, TileMatrixSets tileMatrixSetId, String datetime, List<AllCollections> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionStyleMapGetTileSet(AllCollections collectionId, String styleId, TileMatrixSets tileMatrixSetId, List<AllCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionStyleMapGetTileSetsList(AllCollections collectionId, String styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionStyleVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, VectorTilesCollections collectionId, String styleId, TileMatrixSets tileMatrixSetId, String datetime, List<VectorTilesCollections> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionStyleVectorGetTileSet(VectorTilesCollections collectionId, String styleId, TileMatrixSets tileMatrixSetId, List<VectorTilesCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionStyleVectorGetTileSetsList(VectorTilesCollections collectionId, String styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> collectionVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, VectorTilesCollections collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<VectorTilesCollections> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> collectionVectorGetTileSet(VectorTilesCollections collectionId, TileMatrixSets tileMatrixSetId, List<VectorTilesCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> collectionVectorGetTileSetsList(VectorTilesCollections collectionId, String f) {
        return null;
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
    public ResponseEntity<CollectionInfo> getCollection(AllCollections collectionId, String f) {
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
    public ResponseEntity<String> datasetMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, TileMatrixSets tileMatrixSetId, String datetime, List<AllCollections> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> datasetMapGetTileSet(TileMatrixSets tileMatrixSetId, List<AllCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetMapGetTileSetsList(String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> datasetStyleMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, Styles styleId, TileMatrixSets tileMatrixSetId, String datetime, List<AllCollections> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> datasetStyleMapGetTileSet(Styles styleId, TileMatrixSets tileMatrixSetId, List<AllCollections> collections, String f) {
        return null;
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetStyleMapGetTileSetsList(Styles styleId, String f) {
        return null;
    }

    @Override
    public ResponseEntity<String> datasetStyleVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol, Styles styleId, TileMatrixSets tileMatrixSetId, String datetime, List<VectorTilesCollections> collections, List<String> subset, String crs, String subsetCrs, String f) {
        return null;
    }

    @Override
    public ResponseEntity<TileSet> datasetStyleVectorGetTileSet(Styles styleId, TileMatrixSets tileMatrixSetId, List<AllCollections> collections, String f) {
        return null;
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
}
