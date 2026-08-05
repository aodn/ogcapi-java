package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.exception.InvalidParameterException;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;
import au.org.aodn.ogcapi.server.core.service.das.DasTilerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController("TileRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext/tiles")
public class RestExtApi {

    @Autowired
    protected DasTilerService dasTilerService;

    @Autowired
    protected ObjectMapper mapper;

    @Operation(
            summary = "List the renderable tiler products of a collection",
            description = "The discovery call a map client makes before requesting tiles: it supplies every " +
                    "value the tile routes need, including ready-to-use URL templates.\n\n" +
                    "`tile_types` is a capability list — what this service can serve today, not a property of " +
                    "the data. It reflects the capability DAS reports per product: a product DAS marks as " +
                    "visual-capable gives `[\"visual\", \"data\"]`, one it does not gives `[\"data\"]`. " +
                    "Two-variable products (e.g. `ucur+vcur`) are always `[\"data\"]` — they cannot be " +
                    "colourised — and a single-variable product may be `[\"data\"]` too when its variable is " +
                    "not renderable. Variable arity is used only as a fallback against an older DAS that does " +
                    "not report capability. Each " +
                    "capability carries its own template(s): `visual_tile_url_template` + `legend_url` when " +
                    "`\"visual\"` is present, `data_tile_url_template` + `data_manifest_url_template` when " +
                    "`\"data\"` is present. For two-variable products the `variable` array order (e.g. " +
                    "`[\"UCUR\", \"VCUR\"]`) is the R/G channel order the data-tile shader decodes. A " +
                    "collection with no tiler products returns 200 with an empty array."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "The products of the collection; an empty array if it has none.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "products": [
                                        {
                                          "id": "model_sea_level_anomaly_gridded_realtime:gsla",
                                          "variable": "GSLA",
                                          "tile_types": ["visual", "data"],
                                          "available_dates": ["2024-01-01", "2024-01-02"],
                                          "full_date_range": {"start": "2020-01-01", "end": "2024-01-02"},
                                          "visual_tile_url_template": "/api/v1/ogc/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/map/tiles/WebMercatorQuad/{z}/{x}/{y}?dataset=model_sea_level_anomaly_gridded_realtime&variable=gsla&datetime={datetime}&f=png",
                                          "legend_url": "/api/v1/ogc/ext/tiles/colormaps/{colormap}/legend",
                                          "data_tile_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/{lod}/{x}/{y}?dataset=model_sea_level_anomaly_gridded_realtime&variable=gsla&datetime={datetime}",
                                          "data_manifest_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/manifest?dataset=model_sea_level_anomaly_gridded_realtime&variable=gsla&datetime={datetime}"
                                        },
                                        {
                                          "id": "model_sea_level_anomaly_gridded_realtime:ucur+vcur",
                                          "variable": ["UCUR", "VCUR"],
                                          "tile_types": ["data"],
                                          "available_dates": ["2024-01-01", "2024-01-02"],
                                          "full_date_range": {"start": "2020-01-01", "end": "2024-01-02"},
                                          "data_tile_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/{lod}/{x}/{y}?dataset=model_sea_level_anomaly_gridded_realtime&variable=ucur%2Bvcur&datetime={datetime}",
                                          "data_manifest_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/manifest?dataset=model_sea_level_anomaly_gridded_realtime&variable=ucur%2Bvcur&datetime={datetime}"
                                        },
                                        {
                                          "id": "model_sea_level_anomaly_gridded_realtime:wdir",
                                          "variable": "WDIR",
                                          "tile_types": ["data"],
                                          "available_dates": ["2024-01-01", "2024-01-02"],
                                          "full_date_range": {"start": "2020-01-01", "end": "2024-01-02"},
                                          "data_tile_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/{lod}/{x}/{y}?dataset=model_sea_level_anomaly_gridded_realtime&variable=wdir&datetime={datetime}",
                                          "data_manifest_url_template": "/api/v1/ogc/ext/tiles/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/data_tiles/manifest?dataset=model_sea_level_anomaly_gridded_realtime&variable=wdir&datetime={datetime}"
                                        }
                                      ]
                                    }"""))),
            @ApiResponse(responseCode = "429", description = "Upstream rate limit reached.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "DAS unreachable, errored, or rejected this service's API key.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/collections/{collectionId}/products")
    public ResponseEntity<JsonNode> getCollectionProducts(
            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Collection identifier (metadata record UUID).",
                    example = "0c9eb39c-9cbe-4c6a-8a10-5867087e703a")
            @PathVariable String collectionId) {
        List<JsonNode> products = dasTilerService.productsForCollection(collectionId);
        JsonNode manifest = dasTilerService.getManifest();
        JsonNode manifestProducts = manifest != null ? manifest.path("products") : null;

        ArrayNode result = mapper.createArrayNode();
        for (JsonNode product : products) {
            String id = product.path("id").asText();
            JsonNode variable = product.path("variable");
            int variableCount = variable.isArray() ? variable.size() : 1;

            ObjectNode entry = mapper.createObjectNode();
            entry.put("id", id);
            entry.set("variable", variable);

            // tile_types is a capability list: what THIS service can serve today, not a property of
            // the data. Visual capability now comes from DAS, which knows whether a variable is
            // actually renderable — arity cannot tell a colourisable scalar from one the renderer
            // has no sensible colouring for. The arity rule survives only as a fallback for a DAS
            // old enough to have no `visual` field, which the OGC-first deployment order requires.
            // Data tiles still follow arity: the shader packs one or two channels, and DAS config
            // validation guarantees nothing longer reaches here.
            boolean canVisual = product.has("visual")
                    ? product.path("visual").asBoolean()
                    : variableCount == 1;
            boolean canData = variableCount == 1 || variableCount == 2;
            ArrayNode tileTypes = mapper.createArrayNode();
            if (canVisual) {
                tileTypes.add("visual");
            }
            if (canData) {
                tileTypes.add("data");
            }
            entry.set("tile_types", tileTypes);

            JsonNode availability = manifestProducts != null ? manifestProducts.path(id) : null;
            entry.set("available_dates", availability != null && !availability.isMissingNode()
                    ? availability.path("available_dates") : mapper.createArrayNode());
            entry.set("full_date_range", availability != null && !availability.isMissingNode()
                    ? availability.path("full_date_range") : mapper.createObjectNode());

            // The tile routes take dataset and variable separately, so split the product id on its
            // first ':'. That split is the only place the id is treated as anything but opaque. The
            // variable half of a two-variable product contains '+' (e.g. ucur+vcur), which URLEncoder
            // renders as %2B — without that a query string would decode it back to a space.
            int sep = id.indexOf(':');
            String datasetPart = sep >= 0 ? id.substring(0, sep) : id;
            String variablePart = sep >= 0 ? id.substring(sep + 1) : "";
            String encodedDataset = URLEncoder.encode(datasetPart, StandardCharsets.UTF_8);
            String encodedVariable = URLEncoder.encode(variablePart, StandardCharsets.UTF_8);

            if (canVisual) {
                entry.put("visual_tile_url_template",
                        "/api/v1/ogc/collections/" + collectionId + "/map/tiles/WebMercatorQuad/{z}/{x}/{y}"
                                + "?dataset=" + encodedDataset + "&variable=" + encodedVariable
                                + "&datetime={datetime}&f=png");
                entry.put("legend_url", "/api/v1/ogc/ext/tiles/colormaps/{colormap}/legend");
            }

            if (canData) {
                entry.put("data_tile_url_template",
                        "/api/v1/ogc/ext/tiles/collections/" + collectionId + "/data_tiles/{lod}/{x}/{y}"
                                + "?dataset=" + encodedDataset + "&variable=" + encodedVariable
                                + "&datetime={datetime}");
                entry.put("data_manifest_url_template",
                        "/api/v1/ogc/ext/tiles/collections/" + collectionId + "/data_tiles/manifest"
                                + "?dataset=" + encodedDataset + "&variable=" + encodedVariable
                                + "&datetime={datetime}");
            }

            result.add(entry);
        }

        ObjectNode body = mapper.createObjectNode();
        body.set("products", result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300, must-revalidate")
                .body(body);
    }

    @Operation(
            summary = "Retrieve a DAS data tile of a collection",
            description = "Proxies a **value-encoded** data tile produced by the AODN data-access-service for " +
                    "the portal's WebGL renderer. This is **not** a slippy-map/OGC tile and **not** a display " +
                    "image:\n\n" +
                    "- `lod` is a **product-local level of detail**, `1` = coarsest (at most 4 levels); it is " +
                    "not a Web Mercator zoom.\n" +
                    "- `x`/`y` are the **chunk column/row** within that LOD's grid, read from the manifest's " +
                    "`lods[].grid` — not slippy XYZ. Their upper bounds are computed per product inside DAS " +
                    "and are unknowable here, so out-of-grid coordinates return 404 from upstream.\n" +
                    "- The PNG carries packed values, not colours: a scalar product encodes a 24-bit value in " +
                    "RGB with A as the validity mask; a two-variable product encodes the first variable in R, " +
                    "the second in G and a joint mask in B (channel order follows the product's `variable` " +
                    "order).\n\n" +
                    "Fetch `GET .../data_tiles/manifest` **first** for the decode ranges (`valueRange`, or " +
                    "`uRange`+`vRange`) and LOD geometry. The bytes are a `CACHE_VERSION`-versioned " +
                    "DAS↔shader contract: intermediaries must forward them verbatim and never recompress, " +
                    "transcode or otherwise transform the image.\n\n" +
                    "Valid `dataset`, `variable` and `datetime` values come from " +
                    "`GET /api/v1/ogc/ext/tiles/collections/{collectionId}/products` — use each product's " +
                    "ready-made `data_tile_url_template`.",
            tags = {"Data Tiles"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The value-encoded data tile (opaque PNG payload).",
                    content = @Content(mediaType = "image/png",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "`dataset` or `variable` missing, `variable` " +
                    "containing a space (an unencoded `+`), `datetime` not `YYYY-MM-DD`, `lod` below 1, or " +
                    "negative `x`/`y`.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DAS reported an unknown product " +
                    "(`{dataset}:{variable}`), an unavailable date, or an out-of-range LOD or chunk. " +
                    "Forwarded from DAS, which owns the product catalogue.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "DAS could not process the request (e.g. a " +
                    "malformed date).",
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
    @GetMapping("/collections/{collectionId}/data_tiles/{lod}/{x}/{y}")
    public ResponseEntity<byte[]> getCollectionDataTile(
            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "The metadata record UUID.",
                    example = "0c9eb39c-9cbe-4c6a-8a10-5867087e703a")
            @PathVariable String collectionId,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Product-local level of detail, `1` = coarsest. Not a Web Mercator zoom.",
                    schema = @Schema(type = "integer", minimum = "1"), example = "1")
            @PathVariable Integer lod,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Chunk column within the LOD grid (from the manifest's `lods[].grid`).",
                    schema = @Schema(type = "integer", minimum = "0"), example = "0")
            @PathVariable Integer x,

            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Chunk row within the LOD grid (from the manifest's `lods[].grid`).",
                    schema = @Schema(type = "integer", minimum = "0"), example = "0")
            @PathVariable Integer y,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Dataset name — the `{dataset}` half of a DAS product id. Must be one of the " +
                            "collection's data assets.",
                    example = "model_sea_level_anomaly_gridded_realtime")
            @RequestParam(required = false) String dataset,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Variable name — the `{variable}` half of a DAS product id, combined with " +
                            "`dataset` as `{dataset}:{variable}`. A two-variable value such as `ucur+vcur` " +
                            "must have its `+` percent-encoded as `%2B`.",
                    example = "gsla")
            @RequestParam(required = false) String variable,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Date to decode, strict `YYYY-MM-DD` — not an RFC 3339 date-time. Must be " +
                            "one of the product's `available_dates`.",
                    example = "2024-01-01")
            @RequestParam(required = false) String datetime) {

        // Unlike the visual route's z (bounded 0..24 by WebMercatorQuad), the LOD grid is computed
        // per product inside DAS and is unknowable here — so only the floors are checked locally and
        // out-of-grid coordinates are left for DAS to 404.
        if (lod == null || lod < 1) {
            throw new InvalidParameterException(
                    "lod must be >= 1 (1 is the coarsest level); it is a product-local LOD, not a Web Mercator zoom");
        }
        if (x == null || x < 0 || y == null || y < 0) {
            throw new InvalidParameterException("x and y (chunk column/row) must be >= 0");
        }
        validateProductParams(dataset, variable, datetime);

        // DAS identifies a product by the combined {dataset}:{variable} id.
        String product = dataset + ":" + variable;
        DasTilerService.DasTileResult tile = dasTilerService.getDataTile(product, datetime, lod, x, y);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tile.contentType()));
        if (tile.cacheControl() != null) {
            response.header(HttpHeaders.CACHE_CONTROL, tile.cacheControl());
        }
        return response.body(tile.body());
    }

    @Operation(
            summary = "Retrieve the data-tile decode manifest for a product",
            description = "Returns the per-date manifest a client must fetch **before** requesting data " +
                    "tiles: `bounds`, per-LOD `grid`/`chunkPx`/`storedPx`/`padding`/`zoomThreshold`, and the " +
                    "decode ranges — `valueRange` for a scalar product, or `uRange`+`vRange` for a " +
                    "two-variable product, plus `flagValues`/`flagMeanings` for a categorical product. These " +
                    "values and the tile byte layout are a `CACHE_VERSION`-versioned DAS↔shader contract; " +
                    "forward them unchanged.\n\n" +
                    "Valid `dataset`, `variable` and `datetime` values come from " +
                    "`GET /api/v1/ogc/ext/tiles/collections/{collectionId}/products` — use each product's " +
                    "ready-made `data_manifest_url_template`.",
            tags = {"Data Tiles"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The data-tile decode manifest.",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "`dataset` or `variable` missing, `variable` " +
                    "containing a space (an unencoded `+`), or `datetime` not `YYYY-MM-DD`.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DAS reported an unknown product " +
                    "(`{dataset}:{variable}`) or an unavailable date. Forwarded from DAS, which owns " +
                    "the product catalogue.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "DAS could not process the request (e.g. a " +
                    "malformed date).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Upstream rate limit reached.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "The tile service is unavailable. The cause is " +
                    "deliberately not described and is logged server-side instead.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/collections/{collectionId}/data_tiles/manifest")
    public ResponseEntity<JsonNode> getCollectionDataManifest(
            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "The metadata record UUID.",
                    example = "0c9eb39c-9cbe-4c6a-8a10-5867087e703a")
            @PathVariable String collectionId,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Dataset name — the `{dataset}` half of a DAS product id. Must be one of the " +
                            "collection's data assets.",
                    example = "model_sea_level_anomaly_gridded_realtime")
            @RequestParam(required = false) String dataset,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Variable name — the `{variable}` half of a DAS product id. A two-variable " +
                            "value such as `ucur+vcur` must have its `+` percent-encoded as `%2B`.",
                    example = "gsla")
            @RequestParam(required = false) String variable,

            @Parameter(in = ParameterIn.QUERY, required = true,
                    description = "Date to decode, strict `YYYY-MM-DD`. Must be one of the product's " +
                            "`available_dates`.",
                    example = "2024-01-01")
            @RequestParam(required = false) String datetime) {

        validateProductParams(dataset, variable, datetime);

        String product = dataset + ":" + variable;
        DasTilerService.DasJsonResult manifest = dasTilerService.getDataManifest(product, datetime);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (manifest.cacheControl() != null) {
            response.header(HttpHeaders.CACHE_CONTROL, manifest.cacheControl());
        }
        return response.body(manifest.body());
    }

    private void validateProductParams(String dataset, String variable, String datetime) {
        if (dataset == null || dataset.isBlank()) {
            throw new InvalidParameterException("dataset is required");
        }
        if (variable == null || variable.isBlank()) {
            throw new InvalidParameterException("variable is required");
        }
        // A space can only reach here from an unencoded '+' in the query string ('ucur+vcur'
        // decodes to 'ucur vcur')
        if (variable.contains(" ")) {
            throw new InvalidParameterException(
                    "variable must not contain spaces; percent-encode the '+' of a two-variable " +
                            "product as %2B (e.g. variable=ucur%2Bvcur)");
        }
        if (datetime == null || !datetime.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new InvalidParameterException("datetime is required and must be YYYY-MM-DD");
        }
    }

    @Operation(
            summary = "List the available colormaps",
            description = "Names accepted by the tile route's `colormap` parameter and the legend endpoint. " +
                    "A `custom` colormap with `mode: categorical` maps discrete flag values to fixed colours " +
                    "and rejects `rescale`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The available colormaps.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "custom": [
                                        {"name": "mcs_category", "mode": "categorical"}
                                      ],
                                      "rio_tiler": ["viridis", "viridis_r", "plasma", "rdbu_r"]
                                    }"""))),
            @ApiResponse(responseCode = "502", description = "DAS unreachable, errored, or rejected this service's API key.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/colormaps")
    public ResponseEntity<JsonNode> getColormaps() {
        return ResponseEntity.ok(dasTilerService.getColormaps());
    }

    @Operation(
            summary = "Render a colour legend for a colormap",
            description = "Renders a PNG colour bar for use as a map legend. Pass the same `rescale` used on " +
                    "the tile request to get tick labels; without it only the bare bar is drawn."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The rendered legend image.",
                    content = @Content(mediaType = "image/png",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "`rescale` is malformed, or was supplied for a " +
                    "categorical colormap.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No colormap exists with that name.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "`width`/`height` outside 10-2048, or " +
                    "`orientation` neither `horizontal` nor `vertical`.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "DAS unreachable, errored, or rejected this service's API key.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/colormaps/{name}/legend")
    public ResponseEntity<byte[]> getColormapLegend(
            @Parameter(in = ParameterIn.PATH, required = true,
                    description = "Colormap name, as listed by `GET /api/v1/ogc/ext/tiles/colormaps`.",
                    example = "viridis")
            @PathVariable String name,

            @Parameter(in = ParameterIn.QUERY,
                    description = "Value range to label the bar with, as `min,max`. Rejected for categorical colormaps.",
                    example = "-1,1")
            @RequestParam(required = false) String rescale,

            @Parameter(in = ParameterIn.QUERY, description = "Image width in pixels.",
                    schema = @Schema(type = "integer", minimum = "10", maximum = "2048", defaultValue = "256"))
            @RequestParam(required = false) Integer width,

            @Parameter(in = ParameterIn.QUERY, description = "Image height in pixels.",
                    schema = @Schema(type = "integer", minimum = "10", maximum = "2048", defaultValue = "40"))
            @RequestParam(required = false) Integer height,

            @Parameter(in = ParameterIn.QUERY,
                    description = "Direction the colour bar runs: `horizontal` (left to right) or `vertical` " +
                            "(top to bottom).",
                    schema = @Schema(allowableValues = {"horizontal", "vertical"}, defaultValue = "horizontal"))
            @RequestParam(required = false) String orientation) {

        DasTilerService.DasTileResult legend = dasTilerService.getLegend(name, rescale, width, height, orientation);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(legend.contentType()));
        if (legend.cacheControl() != null) {
            response.header(HttpHeaders.CACHE_CONTROL, legend.cacheControl());
        }
        return response.body(legend.body());
    }
}
