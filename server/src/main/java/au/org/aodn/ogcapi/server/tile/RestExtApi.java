package au.org.aodn.ogcapi.server.tile;

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
                    "value the tile route needs, including a ready-to-use `tile_url_template`.\n\n" +
                    "`tile_types` is a capability list — what this service can serve today, not a property of " +
                    "the data. Scalar products give `[\"visual\"]`; multi-variable ones give `[]`. A collection " +
                    "with no tiler products returns 200 with an empty array."
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
                                          "tile_types": ["visual"],
                                          "available_dates": ["2024-01-01", "2024-01-02"],
                                          "full_date_range": {"start": "2020-01-01", "end": "2024-01-02"},
                                          "tile_url_template": "/api/v1/ogc/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/map/tiles/WebMercatorQuad/{z}/{x}/{y}?product=model_sea_level_anomaly_gridded_realtime%3Agsla&datetime={datetime}&f=png",
                                          "legend_url": "/api/v1/ogc/ext/tiles/colormaps/{colormap}/legend"
                                        },
                                        {
                                          "id": "model_sea_level_anomaly_gridded_realtime:ucur+vcur",
                                          "variable": ["UCUR", "VCUR"],
                                          "tile_types": [],
                                          "available_dates": ["2024-01-01", "2024-01-02"],
                                          "full_date_range": {"start": "2020-01-01", "end": "2024-01-02"},
                                          "tile_url_template": "/api/v1/ogc/collections/0c9eb39c-9cbe-4c6a-8a10-5867087e703a/map/tiles/WebMercatorQuad/{z}/{x}/{y}?product=model_sea_level_anomaly_gridded_realtime%3Aucur%2Bvcur&datetime={datetime}&f=png",
                                          "legend_url": "/api/v1/ogc/ext/tiles/colormaps/{colormap}/legend"
                                        }
                                      ]
                                    }"""))),
            @ApiResponse(responseCode = "429", description = "Upstream rate limit reached.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "502", description = "DAS unreachable, errored, or rejected this service's API key.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class)))})
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
            boolean isMultiVariable = variable.isArray() && variable.size() > 1;

            ObjectNode entry = mapper.createObjectNode();
            entry.put("id", id);
            entry.set("variable", variable);

            ArrayNode tileTypes = mapper.createArrayNode();
            if (!isMultiVariable) {
                tileTypes.add("visual");
            }
            entry.set("tile_types", tileTypes);

            JsonNode availability = manifestProducts != null ? manifestProducts.path(id) : null;
            entry.set("available_dates", availability != null && !availability.isMissingNode()
                    ? availability.path("available_dates") : mapper.createArrayNode());
            entry.set("full_date_range", availability != null && !availability.isMissingNode()
                    ? availability.path("full_date_range") : mapper.createObjectNode());

            String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
            entry.put("tile_url_template",
                    "/api/v1/ogc/collections/" + collectionId + "/map/tiles/WebMercatorQuad/{z}/{x}/{y}"
                            + "?product=" + encodedId + "&datetime={datetime}&f=png");

            entry.put("legend_url", "/api/v1/ogc/ext/tiles/colormaps/{colormap}/legend");

            result.add(entry);
        }

        ObjectNode body = mapper.createObjectNode();
        body.set("products", result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300, must-revalidate")
                .body(body);
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
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class)))})
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
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "404", description = "No colormap exists with that name.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "422", description = "`width`/`height` outside 10-2048, or " +
                    "`orientation` neither `horizontal` nor `vertical`.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "502", description = "DAS unreachable, errored, or rejected this service's API key.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "503", description = "DAS is still warming up.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class))),
            @ApiResponse(responseCode = "504", description = "DAS did not respond in time.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = au.org.aodn.ogcapi.tile.model.Exception.class)))})
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
