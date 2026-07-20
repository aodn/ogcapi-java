package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.mapper.BinaryResponseToBytes;
import au.org.aodn.ogcapi.server.core.mapper.StacToTileSetWmWGS84Q;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.mapper.StacToInlineResponse2002;
import au.org.aodn.ogcapi.server.core.service.DasTilerService;
import au.org.aodn.ogcapi.tile.api.*;
import au.org.aodn.ogcapi.tile.model.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Implements the rest api of tile
 */
@RestController("TileRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements CollectionsApi, MapApi, StylesApi, TileMatrixSetsApi, TilesApi {

    @Autowired
    protected RestService restService;

    @Autowired
    protected StacToInlineResponse2002 stacToInlineResponse2002;

    @Autowired
    protected StacToTileSetWmWGS84Q stacToTileSet;

    @Autowired
    protected BinaryResponseToBytes binaryResponseToByte;

    @Autowired
    protected DasTilerService dasTilerService;

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

    /**
     * Generated stub for the OGC collection-map-tile route — disabled via {@code @Hidden} (see
     * {@code CustomMvcRegistrations}, which skips request-mapping registration for any method
     * carrying that annotation) so {@link #getCollectionVisualMapTile} below can serve the same
     * path with a hand-written signature instead.
     */
    @Hidden
    @Override
    public ResponseEntity<String> collectionMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    /**
     * Serves a DAS "visual tile" (colourised raster PNG/WebP for a gridded Zarr ocean product)
     * through the OGC API - Tiles collection map-tile route, attaching the DAS API key
     * server-side so the DAS origin itself never needs to be browser-facing.
     * <p>
     * Deliberately non-strict OGC: {@code tileMatrix}=z, {@code tileRow}=x, {@code tileCol}=y
     * (slippy {z}/{x}/{y}), matching the convention already shipped by the vector-tile route
     * ({@code ElasticSearch.searchCollectionVectorTile}, used by {@code datasetVectorGetTile}
     * below) and the frontend's {@code VectorTileLayers.tsx} template — not strict OGC
     * row=y/col=x semantics. A generic OGC client would disagree; Mapbox does not care.
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/collections/{collectionId}/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
    public ResponseEntity<?> getCollectionVisualMapTile(
            @PathVariable String collectionId,
            @PathVariable String tileMatrixSetId,
            @PathVariable Integer tileMatrix,
            @PathVariable Integer tileRow,
            @PathVariable Integer tileCol,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String datetime,
            @RequestParam(required = false) String colormap,
            @RequestParam(required = false) String rescale,
            @RequestParam(required = false) String cv,
            @RequestParam(required = false, defaultValue = "png") String f) {

        if (!"WebMercatorQuad".equals(tileMatrixSetId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", "Unsupported tileMatrixSetId, only WebMercatorQuad is supported"));
        }
        if (product == null || product.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "product is required"));
        }
        if (datetime == null || !datetime.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return ResponseEntity.badRequest().body(Map.of("detail", "datetime is required and must be YYYY-MM-DD"));
        }
        if (!"png".equals(f) && !"webp".equals(f)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "f must be 'png' or 'webp'"));
        }
        if (!dasTilerService.isProductInCollection(collectionId, product)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", "product '" + product + "' not found in collection '" + collectionId + "'"));
        }

        DasTilerService.DasTileResult tile = dasTilerService.getVisualTile(
                product, datetime, tileMatrix, tileRow, tileCol, f, colormap, rescale, cv);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tile.contentType()));
        if (tile.cacheControl() != null) {
            response.header(HttpHeaders.CACHE_CONTROL, tile.cacheControl());
        }
        return response.body(tile.body());
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
                null,
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
    public ResponseEntity<?> datasetVectorGetTile(String tileMatrix, Integer tileRow, Integer tileCol,
                                                  TileMatrixSets tileMatrixSetId, String datetime,
                                                  List<String> collections, List<String> subset,
                                                  String crs, String subsetCrs, String f) {

        OGCMediaTypeMapper type = OGCMediaTypeMapper.convert(f, OGCMediaTypeMapper.mapbox);

        switch(type) {
            case mapbox -> {
                return restService.getVectorTileOfCollection(
                        tileMatrixSetId,
                        collections,
                        Integer.valueOf(tileMatrix),
                        tileRow,
                        tileCol,
                        binaryResponseToByte::convert);
            }

            default -> {
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            }
        }
    }

    @Override
    public ResponseEntity<TileSet> datasetVectorGetTileSet(TileMatrixSets tileMatrixSetId, List<String> collections, String f) {
        switch(tileMatrixSetId) {
            case WEBMERCATORQUAD -> {
                //TODO: The return set seems not correct more study needed.
                return restService.getTileSetsListOfCollection(
                        collections,
                        "-score",
                        OGCMediaTypeMapper.convert(f),
                        stacToTileSet::convert);
            }
            default -> {
                // We support WEBMERCATORQUAD at the moment, so if it isn't return empty set.
                return ResponseEntity.status(HttpStatus.OK).body(new TileSet());
            }
        }
    }

    @Override
    public ResponseEntity<InlineResponse2002> datasetVectorGetTileSetsList(String f) {
        return restService.getTileSetsListOfCollection(
                null,
                "-score",
                OGCMediaTypeMapper.convert(f),
                stacToInlineResponse2002::convert);
    }
}
