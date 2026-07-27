package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.mapper.BinaryResponseToBytes;
import au.org.aodn.ogcapi.server.core.mapper.StacToTileSetWmWGS84Q;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import au.org.aodn.ogcapi.server.core.mapper.StacToInlineResponse2002;
import au.org.aodn.ogcapi.server.core.exception.InvalidParameterException;
import au.org.aodn.ogcapi.server.core.exception.ResourceNotFoundException;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;
import au.org.aodn.ogcapi.server.core.service.das.DasTilerService;
import au.org.aodn.ogcapi.tile.api.*;
import au.org.aodn.ogcapi.tile.model.*;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Implements the rest api of tile
 */
@RestController("TileRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements CollectionsApi, MapApi, StylesApi, TileMatrixSetsApi, TilesApi {

    // OGC's WebMercatorQuad TileMatrixSet defines levels 0-24.
    private static final int MAX_ZOOM = 24;

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

    @Hidden
    @Override
    public ResponseEntity<String> collectionMapGetTile(String tileMatrix, Integer tileRow, Integer tileCol, String collectionId, TileMatrixSets tileMatrixSetId, String datetime, List<String> collections, List<String> subset, String crs, String subsetCrs, String bgcolor, Boolean transparent, String f) {
        return null;
    }

    /**
     * Serves a DAS "visual tile" (colourised raster PNG/WebP for a gridded Zarr ocean product)
     * through the OGC API - Tiles collection map-tile route
     * <p>
     * Deliberately non-strict OGC: {@code tileMatrix}=z, {@code tileRow}=x, {@code tileCol}=y
     * (slippy {z}/{x}/{y}), matching the convention already shipped by the vector-tile route
     * ({@code ElasticSearch.searchCollectionVectorTile}, used by {@code datasetVectorGetTile}
     * below) and the frontend's {@code VectorTileLayers.tsx} template — not strict OGC
     * row=y/col=x semantics. A generic OGC client would disagree; Mapbox does not care.
     */
    @Operation(
            summary = "Retrieve a DAS visual map tile of a collection",
            description = "Proxies a colourised raster tile (PNG/WebP) rendered by the AODN " +
                    "data-access-service from a gridded Zarr ocean product.\n\n" +
                    "**Not strict OGC:** the path is slippy-map `{z}/{x}/{y}`, so `tileRow` is **x** and " +
                    "`tileCol` is **y** — the reverse of OGC's row/column order. This matches the vector-tile " +
                    "route and the frontend's Mapbox templates.\n\n" +
                    "Valid `dataset`, `variable` and `datetime` values come from " +
                    "`GET /api/v1/ogc/ext/tiles/collections/{collectionId}/products` — use each product's " +
                    "ready-made `tile_url_template`.",
            tags = {"Map Tiles"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The rendered map tile. Tiles outside the " +
                    "product's extent are fully transparent images, not an error.",
                    content = {
                            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "400", description = "`dataset`, `variable` or `datetime` missing, " +
                    "`datetime` not `YYYY-MM-DD`, `f` neither `png` nor `webp`, or z/x/y out of range.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "`tileMatrixSetId` is not `WebMercatorQuad`, " +
                    "`dataset` is not in the collection, or there is no data for that date.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The product cannot be rendered as a visual " +
                    "tile — e.g. a multi-variable product (variable such as `ucur+vcur`).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Upstream rate limit reached.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "The tile service is unavailable. The cause is " +
                    "deliberately not described — it is always a fault on this side, never something the caller " +
                    "can act on — and is logged server-side instead.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(value = "/collections/{collectionId}/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
    public ResponseEntity<byte[]> getCollectionVisualMapTile(
            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "The metadata record UUID.",
                    example = "0c9eb39c-9cbe-4c6a-8a10-5867087e703a")
            @PathVariable String collectionId,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Only `WebMercatorQuad` is supported; any other value returns 404.",
                    schema = @Schema(allowableValues = {"WebMercatorQuad"}, defaultValue = "WebMercatorQuad"))
            @PathVariable String tileMatrixSetId,

            @Parameter(in = ParameterIn.PATH, required = true, description = "Zoom level **z**.",
                    schema = @Schema(type = "integer", minimum = "0", maximum = "24"), example = "2")
            @PathVariable Integer tileMatrix,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Tile **x**, not the OGC row. Range 0 to 2^z - 1.",
                    schema = @Schema(type = "integer", minimum = "0"), example = "3")
            @PathVariable Integer tileRow,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Tile **y**, not the OGC column. Range 0 to 2^z - 1.",
                    schema = @Schema(type = "integer", minimum = "0"), example = "2")
            @PathVariable Integer tileCol,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Dataset name — the `{dataset}` half of a DAS product id. Must be one of the " +
                            "collection's data assets.",
                    example = "model_sea_level_anomaly_gridded_realtime")
            @RequestParam(required = false) String dataset,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Variable name — the `{variable}` half of a DAS product id. Combined with " +
                            "`dataset` as `{dataset}:{variable}` to identify the product to render.",
                    example = "gsla")
            @RequestParam(required = false) String variable,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Date to render, strict `YYYY-MM-DD` — not an RFC 3339 date-time. Must be " +
                            "one of the product's `available_dates`.",
                    example = "2024-01-01")
            @RequestParam(required = false) String datetime,

            @Parameter(in = ParameterIn.QUERY,
                    description = "Colormap name. Omit to use the product's default.",
                    example = "viridis")
            @RequestParam(required = false) String colormap,

            @Parameter(in = ParameterIn.QUERY,
                    description = "Colour range as `min,max`. Supply it for time-series maps — without it " +
                            "each date is scaled to its own min/max, so colours are not comparable across " +
                            "dates. Rejected for categorical products.",
                    example = "-1,1")
            @RequestParam(required = false) String rescale,

            @Parameter(in = ParameterIn.QUERY, description = "Output image format.",
                    schema = @Schema(allowableValues = {"png", "webp"}, defaultValue = "png"))
            @RequestParam(required = false, defaultValue = "png") String f) {

        if (!TileMatrixSets.WEBMERCATORQUAD.toString().equals(tileMatrixSetId)) {
            throw new ResourceNotFoundException("Unsupported tileMatrixSetId, only WebMercatorQuad is supported");
        }

        if (tileMatrix == null || tileMatrix < 0 || tileMatrix > MAX_ZOOM) {
            throw new InvalidParameterException("tileMatrix (z) must be between 0 and " + MAX_ZOOM);
        }
        int maxIndex = (1 << tileMatrix) - 1;
        if (tileRow == null || tileRow < 0 || tileRow > maxIndex
                || tileCol == null || tileCol < 0 || tileCol > maxIndex) {
            throw new InvalidParameterException("tileRow/tileCol out of range for tileMatrix=" + tileMatrix
                    + "; valid range is 0-" + maxIndex);
        }
        if (dataset == null || dataset.isBlank()) {
            throw new InvalidParameterException("dataset is required");
        }
        if (variable == null || variable.isBlank()) {
            throw new InvalidParameterException("variable is required");
        }
        if (datetime == null || !datetime.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new InvalidParameterException("datetime is required and must be YYYY-MM-DD");
        }
        if (!"png".equals(f) && !"webp".equals(f)) {
            throw new InvalidParameterException("f must be 'png' or 'webp'");
        }
        if (!dasTilerService.isDatasetInCollection(collectionId, dataset)) {
            throw new ResourceNotFoundException(
                    "dataset '" + dataset + "' not found in collection '" + collectionId + "'");
        }

        // DAS identifies a renderable product by the combined {dataset}:{variable} id.
        String product = dataset + ":" + variable;
        DasTilerService.DasTileResult tile = dasTilerService.getVisualTile(
                product, datetime, tileMatrix, tileRow, tileCol, f, colormap, rescale);

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
