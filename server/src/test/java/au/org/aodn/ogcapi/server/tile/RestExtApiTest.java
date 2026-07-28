package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;
import au.org.aodn.ogcapi.server.core.service.das.DasTilerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the /api/v1/ogc/ext/tiles routes (product/date listing joined from DAS
 * products+manifest, colormaps passthrough, legend proxy). dasTilerService is mocked so these
 * don't need a running DAS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RestExtApiTest extends BaseTestClass {

    @MockitoBean
    protected DasTilerService dasTilerService;

    @Autowired
    protected ObjectMapper mapper;

    private JsonNode singleVariableProduct(String id, String metadataUuid, String variable) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("metadata_uuid", metadataUuid);
        node.put("variable", variable);
        return node;
    }

    private JsonNode multiVariableProduct(String id, String metadataUuid, List<String> variables) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("metadata_uuid", metadataUuid);
        ArrayNode arr = mapper.createArrayNode();
        variables.forEach(arr::add);
        node.set("variable", arr);
        return node;
    }

    private ObjectNode manifestWith(String productId) {
        ObjectNode manifest = mapper.createObjectNode();
        ObjectNode products = mapper.createObjectNode();
        ObjectNode availability = mapper.createObjectNode();
        availability.set("available_dates", mapper.createArrayNode().add("2024-01-01"));
        ObjectNode range = mapper.createObjectNode();
        range.put("start", "2024-01-01");
        range.put("end", "2024-01-01");
        availability.set("full_date_range", range);
        products.set(productId, availability);
        manifest.set("products", products);
        return manifest;
    }

    @Test
    public void verifyCollectionProductsListsMatchingProducts() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(singleVariableProduct("model_sla:gsla", "uuid-a", "GSLA"))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("model_sla:gsla"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals(1, body.get("products").size());

        JsonNode entry = body.get("products").get(0);
        Assertions.assertEquals("model_sla:gsla", entry.get("id").asText());
        Assertions.assertEquals(1, entry.get("available_dates").size());
        // The tile route takes dataset + variable separately, so the product id is split into both.
        String template = entry.get("visual_tile_url_template").asText();
        Assertions.assertTrue(template.contains("dataset=model_sla"), "got: " + template);
        Assertions.assertTrue(template.contains("variable=gsla"), "got: " + template);
        Assertions.assertFalse(template.contains("product="), "product must be split into dataset+variable, got: " + template);
        Assertions.assertFalse(entry.has("source_path"), "source_path must never leak through the ext listing");
    }

    @Test
    public void verifyScalarProductAdvertisesVisualAndDataTileTypes() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(singleVariableProduct("model_sla:gsla", "uuid-a", "GSLA"))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("model_sla:gsla"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        JsonNode entry = response.getBody().get("products").get(0);
        JsonNode tileTypes = entry.get("tile_types");
        Assertions.assertTrue(tileTypes.isArray(), "tile_types must be a capability array, not a scalar");
        Assertions.assertEquals(2, tileTypes.size());
        Assertions.assertEquals("visual", tileTypes.get(0).asText(), "visual is listed first");
        Assertions.assertEquals("data", tileTypes.get(1).asText());

        // Every advertised capability carries its own template(s).
        Assertions.assertTrue(entry.has("visual_tile_url_template"), "visual capability -> visual template");
        Assertions.assertTrue(entry.has("legend_url"), "visual capability -> legend");
        Assertions.assertTrue(entry.has("data_tile_url_template"), "data capability -> data tile template");
        Assertions.assertTrue(entry.has("data_manifest_url_template"), "data capability -> data manifest template");

        String dataTemplate = entry.get("data_tile_url_template").asText();
        Assertions.assertTrue(dataTemplate.contains("/data_tiles/{lod}/{x}/{y}"), "got: " + dataTemplate);
        Assertions.assertTrue(dataTemplate.contains("dataset=model_sla"), "got: " + dataTemplate);
        Assertions.assertTrue(dataTemplate.contains("variable=gsla"), "got: " + dataTemplate);
        String manifestTemplate = entry.get("data_manifest_url_template").asText();
        Assertions.assertTrue(manifestTemplate.contains("/data_tiles/manifest"), "got: " + manifestTemplate);
    }

    @Test
    public void verifyCollectionProductsEmptyWhenNoneMatch() {
        when(dasTilerService.productsForCollection("uuid-none")).thenReturn(List.of());
        when(dasTilerService.getManifest()).thenReturn(mapper.createObjectNode());

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-none/products", JsonNode.class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(0, response.getBody().get("products").size());
    }

    @Test
    public void verifyMultiVariableProductAdvertisesDataTileTypeOnly() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(multiVariableProduct("model_currents:ucur+vcur", "uuid-a", List.of("UCUR", "VCUR")))
        );
        when(dasTilerService.getManifest()).thenReturn(mapper.createObjectNode());

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        JsonNode entry = response.getBody().get("products").get(0);
        // A two-variable product cannot be colourised as a visual tile (DAS rejects it), but it CAN
        // be served as a data tile — so the honest capability list is exactly ["data"].
        JsonNode tileTypes = entry.get("tile_types");
        Assertions.assertTrue(tileTypes.isArray());
        Assertions.assertEquals(1, tileTypes.size());
        Assertions.assertEquals("data", tileTypes.get(0).asText());

        // Visual-only fields must NOT be emitted for a product that can't serve visual tiles.
        Assertions.assertFalse(entry.has("visual_tile_url_template"), "no visual template without the visual capability");
        Assertions.assertFalse(entry.has("legend_url"), "no legend without the visual capability");

        Assertions.assertTrue(entry.has("data_tile_url_template"));
        Assertions.assertTrue(entry.has("data_manifest_url_template"));

        // The '+' in the variable half must be percent-encoded, or a query string decodes it to a space.
        String dataTemplate = entry.get("data_tile_url_template").asText();
        Assertions.assertTrue(dataTemplate.contains("variable=ucur%2Bvcur"), "'+' must be %2B, got: " + dataTemplate);
        Assertions.assertFalse(dataTemplate.contains("variable=ucur+vcur"), "raw '+' would decode to a space, got: " + dataTemplate);
        String manifestTemplate = entry.get("data_manifest_url_template").asText();
        Assertions.assertTrue(manifestTemplate.contains("variable=ucur%2Bvcur"), "'+' must be %2B, got: " + manifestTemplate);
    }

    // --- Data-tile route: value-encoded PNG passthrough, floor-only validation, membership guard ---

    @Test
    public void verifyDataTileReturnsImageWithCacheControl() {
        when(dasTilerService.isDatasetInCollection("uuid-a", "model_sla")).thenReturn(true);
        when(dasTilerService.getDataTile("model_sla:gsla", "2024-01-01", 1, 0, 0)).thenReturn(
                new DasTilerService.DasTileResult(
                        "data-bytes".getBytes(), "image/png", "public, max-age=31536000, immutable"));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", byte[].class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertArrayEquals("data-bytes".getBytes(), response.getBody());
        Assertions.assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        Assertions.assertEquals("public, max-age=31536000, immutable", response.getHeaders().getCacheControl());
    }

    @Test
    public void verifyDataTileRejectsLodBelowOne() {
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/0/0/0"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(dasTilerService, never()).getDataTile(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void verifyDataTileRejectsNegativeXorY() {
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/-1/0"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/-1"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
    }

    @Test
    public void verifyDataTileRejectsMissingOrMalformedParams() {
        // missing dataset
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?variable=gsla&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
        // missing variable
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=model_sla&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
        // datetime not YYYY-MM-DD
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-1-1", ErrorResponse.class).getStatusCode());
    }

    @Test
    public void verifyDataTileNotFoundWhenDatasetNotInCollection() {
        when(dasTilerService.isDatasetInCollection("uuid-a", "wrong")).thenReturn(false);

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=wrong&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        // Membership fails locally, so DAS is never called for the tile.
        verify(dasTilerService, never()).getDataTile(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void verifyDataTileMirrorsUpstreamNotFound() {
        when(dasTilerService.isDatasetInCollection("uuid-a", "model_sla")).thenReturn(true);
        when(dasTilerService.getDataTile("model_sla:gsla", "2024-01-01", 9, 0, 0))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "LOD 9 not in grid"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/9/0/0"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("LOD 9 not in grid", response.getBody().getMessage());
    }

    @Test
    public void verifyDataTileMirrorsUpstreamServiceUnavailable() {
        when(dasTilerService.isDatasetInCollection("uuid-a", "model_sla")).thenReturn(true);
        when(dasTilerService.getDataTile("model_sla:gsla", "2024-01-01", 1, 0, 0))
                .thenThrow(new DasUpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"));

        for (MediaType accept : List.of(MediaType.IMAGE_PNG, MediaType.ALL, MediaType.APPLICATION_JSON)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(accept));

            ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                    getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                            + "?dataset=model_sla&variable=gsla&datetime=2024-01-01",
                    HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(),
                    "upstream 503 must not degrade to 500 under Accept: " + accept);
            Assertions.assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType()),
                    "error body must stay JSON under Accept: " + accept);
            Assertions.assertEquals("Service Unavailable", response.getBody().getMessage());
        }
    }

    // --- Data-manifest route: JSON passthrough with forwarded Cache-Control ---

    @Test
    public void verifyDataManifestReturnsJsonWithCacheControl() {
        ObjectNode manifestBody = mapper.createObjectNode();
        manifestBody.putArray("bounds").add(0).add(0).add(1).add(1);
        when(dasTilerService.isDatasetInCollection("uuid-a", "model_sla")).thenReturn(true);
        when(dasTilerService.getDataManifest("model_sla:gsla", "2024-01-01"))
                .thenReturn(new DasTilerService.DasJsonResult(manifestBody, "public, max-age=31536000, immutable"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01", JsonNode.class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().has("bounds"));
        Assertions.assertEquals("public, max-age=31536000, immutable", response.getHeaders().getCacheControl());
    }

    @Test
    public void verifyDataManifestRejectsMissingOrMalformedParams() {
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?variable=gsla&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?dataset=model_sla&variable=gsla&datetime=not-a-date", ErrorResponse.class).getStatusCode());
    }

    @Test
    public void verifyDataManifestNotFoundWhenDatasetNotInCollection() {
        when(dasTilerService.isDatasetInCollection("uuid-a", "wrong")).thenReturn(false);

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?dataset=wrong&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(dasTilerService, never()).getDataManifest(anyString(), anyString());
    }

    @Test
    public void verifyColormapsPassthrough() {
        ObjectNode body = mapper.createObjectNode();
        body.set("colormaps", mapper.createArrayNode().add("viridis").add("plasma"));
        when(dasTilerService.getColormaps()).thenReturn(body);

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/colormaps", JsonNode.class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(2, response.getBody().get("colormaps").size());
    }

    @Test
    public void verifyColormapLegendReturnsImage() {
        when(dasTilerService.getLegend("viridis", null, null, null, null))
                .thenReturn(new DasTilerService.DasTileResult(
                        "legend-bytes".getBytes(), "image/png", "public, max-age=31536000, immutable"));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/colormaps/viridis/legend", byte[].class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertArrayEquals("legend-bytes".getBytes(), response.getBody());
    }

    /**
     * These routes surface upstream failures through GlobalExceptionHandler, so the body is the
     * service-wide ErrorResponse envelope — which is what the @ApiResponse schemas on RestExtApi
     * now declare. Pinned here so the documented schema and the real one can't drift apart again.
     */
    @Test
    public void verifyUpstreamErrorUsesErrorResponseEnvelope() {
        when(dasTilerService.getLegend("nosuch", null, null, null, null))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "no such colormap"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/colormaps/nosuch/legend", ErrorResponse.class
        );

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertNotNull(response.getBody(), "Body not null");
        Assertions.assertEquals("no such colormap", response.getBody().getMessage(),
                "Message carries the upstream reason");
        Assertions.assertNotNull(response.getBody().getTimestamp(), "Timestamp populated by the handler");
        Assertions.assertTrue(
                response.getBody().getDetails().contains("/tiles/colormaps/nosuch/legend"),
                "Details identify the failing request, got: " + response.getBody().getDetails());
    }
}
