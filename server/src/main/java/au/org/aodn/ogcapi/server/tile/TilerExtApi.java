package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.core.service.DasTilerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Non-OGC "ext" routes that expose DAS tiler metadata (product/date listing, colormaps, legend
 * images) to the frontend, so it doesn't have to hand-assemble DAS URLs or know about DAS at
 * all. Mirrors the existing {@code /api/v1/ogc/ext} namespace precedent in
 * {@code au.org.aodn.ogcapi.server.common.RestExtApi}.
 */
@Slf4j
@RestController("TilerExtApi")
@RequestMapping(value = "/api/v1/ogc/ext/tiler")
@CrossOrigin(origins = "*")
public class TilerExtApi {

    @Autowired
    protected DasTilerService dasTilerService;

    @Autowired
    protected ObjectMapper mapper;

    /**
     * Lists DAS tiler products belonging to {@code collectionId} (matched via the DAS product
     * registry's {@code metadata_uuid} field) along with their available dates and ready-to-use
     * ogcapi tile/legend URL templates, so the frontend never sees DAS's raw {@code source_path}.
     */
    @GetMapping("/collections/{collectionId}/products")
    public ResponseEntity<JsonNode> getCollectionProducts(@PathVariable String collectionId) {
        List<JsonNode> products = dasTilerService.productsForCollection(collectionId);
        JsonNode manifest = dasTilerService.getManifest();
        JsonNode manifestProducts = manifest != null ? manifest.path("products") : null;
        String cacheVersion = manifest != null ? manifest.path("cache_version").asText(null) : null;

        ArrayNode result = mapper.createArrayNode();
        for (JsonNode product : products) {
            String id = product.path("id").asText();
            JsonNode variable = product.path("variable");
            boolean isMultiVariable = variable.isArray() && variable.size() > 1;

            ObjectNode entry = mapper.createObjectNode();
            entry.put("id", id);
            entry.set("variable", variable);
            // Multi-variable products (e.g. ucur+vcur) are rejected by DAS for visual tiles;
            // they are listed here anyway for the future data-tile phase to consume.
            entry.put("type", isMultiVariable ? "data" : "visual");

            JsonNode availability = manifestProducts != null ? manifestProducts.path(id) : null;
            entry.set("available_dates", availability != null && !availability.isMissingNode()
                    ? availability.path("available_dates") : mapper.createArrayNode());
            entry.set("full_date_range", availability != null && !availability.isMissingNode()
                    ? availability.path("full_date_range") : mapper.createObjectNode());

            String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
            String cvSuffix = cacheVersion != null ? "&cv=" + cacheVersion : "";
            entry.put("tile_url_template",
                    "/api/v1/ogc/collections/" + collectionId + "/map/tiles/WebMercatorQuad/{z}/{x}/{y}"
                            + "?product=" + encodedId + "&datetime={datetime}&f=png" + cvSuffix);
            // No per-product default colormap exists yet (see plan's Deferred style-catalog
            // section) — the frontend fills {colormap} in itself, e.g. from GET /colormaps.
            entry.put("legend_url", "/api/v1/ogc/ext/tiler/colormaps/{colormap}/legend"
                    + (cacheVersion != null ? "?cv=" + cacheVersion : ""));

            result.add(entry);
        }

        ObjectNode body = mapper.createObjectNode();
        body.set("products", result);
        if (cacheVersion != null) {
            body.put("cache_version", cacheVersion);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300, must-revalidate")
                .body(body);
    }

    @GetMapping("/colormaps")
    public ResponseEntity<JsonNode> getColormaps() {
        return ResponseEntity.ok(dasTilerService.getColormaps());
    }

    @GetMapping("/colormaps/{name}/legend")
    public ResponseEntity<byte[]> getColormapLegend(
            @PathVariable String name,
            @RequestParam(required = false) String rescale,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false) String orientation,
            @RequestParam(required = false) String cv) {

        DasTilerService.DasTileResult legend = dasTilerService.getLegend(name, rescale, width, height, orientation, cv);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(legend.contentType()));
        if (legend.cacheControl() != null) {
            response.header(HttpHeaders.CACHE_CONTROL, legend.cacheControl());
        }
        return response.body(legend.body());
    }
}
