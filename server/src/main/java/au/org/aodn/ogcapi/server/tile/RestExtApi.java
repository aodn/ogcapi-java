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

@Slf4j
@RestController("TileRestExtApi")
@RequestMapping(value = "/api/v1/ogc/ext/tiles")
public class RestExtApi {

    @Autowired
    protected DasTilerService dasTilerService;

    @Autowired
    protected ObjectMapper mapper;

    @GetMapping("/collections/{collectionId}/products")
    public ResponseEntity<JsonNode> getCollectionProducts(@PathVariable String collectionId) {
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
