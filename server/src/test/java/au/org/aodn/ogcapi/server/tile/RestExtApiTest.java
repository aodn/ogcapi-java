package au.org.aodn.ogcapi.server.tile;

import au.org.aodn.ogcapi.server.BaseTestClass;
import au.org.aodn.ogcapi.server.core.service.DasTilerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

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
                List.of(singleVariableProduct("p1", "uuid-a", "GSLA"))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("p1"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals(1, body.get("products").size());

        JsonNode entry = body.get("products").get(0);
        Assertions.assertEquals("p1", entry.get("id").asText());
        Assertions.assertEquals(1, entry.get("available_dates").size());
        Assertions.assertTrue(entry.get("tile_url_template").asText().contains("product=p1"));
        Assertions.assertFalse(entry.has("source_path"), "source_path must never leak through the ext listing");
    }

    @Test
    public void verifyScalarProductAdvertisesVisualTileTypeOnly() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(singleVariableProduct("p1", "uuid-a", "GSLA"))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("p1"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        JsonNode tileTypes = response.getBody().get("products").get(0).get("tile_types");
        Assertions.assertTrue(tileTypes.isArray(), "tile_types must be a capability array, not a scalar");
        Assertions.assertEquals(1, tileTypes.size());
        Assertions.assertEquals("visual", tileTypes.get(0).asText());
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
    public void verifyMultiVariableProductAdvertisesNoServableTileType() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(multiVariableProduct("p2", "uuid-a", List.of("UCUR", "VCUR")))
        );
        when(dasTilerService.getManifest()).thenReturn(mapper.createObjectNode());

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/products", JsonNode.class
        );

        JsonNode entry = response.getBody().get("products").get(0);
        // DAS rejects multi-variable products for visual tiles and ogcapi has no data-tile
        // route yet, so the honest answer is "nothing servable" — NOT "data", which would
        // advertise an endpoint that 404s. "data" gets appended here once that route exists.
        JsonNode tileTypes = entry.get("tile_types");
        Assertions.assertTrue(tileTypes.isArray());
        Assertions.assertEquals(0, tileTypes.size());
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
}
