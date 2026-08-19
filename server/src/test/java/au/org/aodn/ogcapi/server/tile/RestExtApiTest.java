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

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
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

    /** Spring's MediaType has no constant for it */
    private static final MediaType IMAGE_WEBP = MediaType.valueOf("image/webp");

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

    /** A product from a DAS new enough to report capability explicitly. */
    private JsonNode scalarProductWithVisual(String id, String metadataUuid, String variable, boolean visual) {
        ObjectNode node = (ObjectNode) singleVariableProduct(id, metadataUuid, variable);
        node.put("visual", visual);
        return node;
    }

    private DasTilerService.DasJsonResult manifestWith(String productId) {
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
        return new DasTilerService.DasJsonResult(manifest, "public, max-age=31536000, immutable");
    }

    private DasTilerService.DasJsonResult emptyManifest() {
        return new DasTilerService.DasJsonResult(mapper.createObjectNode(), "public, max-age=31536000, immutable");
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
        when(dasTilerService.getManifest()).thenReturn(emptyManifest());

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
        when(dasTilerService.getManifest()).thenReturn(emptyManifest());

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

    // --- Capability: DAS's explicit `visual` field, with the arity rule as old-DAS fallback ---

    @Test
    public void verifyScalarWithVisualTrueAdvertisesVisualAndData() {
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(scalarProductWithVisual("model_sla:gsla", "uuid-a", "GSLA", true))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("model_sla:gsla"));

        JsonNode entry = getProducts("uuid-a").get(0);

        Assertions.assertEquals(List.of("visual", "data"), tileTypesOf(entry));
        Assertions.assertTrue(entry.has("visual_tile_url_template"));
        Assertions.assertTrue(entry.has("legend_url"));
    }

    @Test
    public void verifyScalarWithVisualFalseAdvertisesDataOnly() {
        // The case arity cannot express: a single-variable product whose variable the renderer has
        // no sensible colouring for. Before DAS reported capability this would have been advertised
        // as visual-capable and rendered a meaningless image.
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(scalarProductWithVisual("model_sla:wdir", "uuid-a", "WDIR", false))
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("model_sla:wdir"));

        JsonNode entry = getProducts("uuid-a").get(0);

        Assertions.assertEquals(List.of("data"), tileTypesOf(entry));
        Assertions.assertFalse(entry.has("visual_tile_url_template"),
                "a product DAS says cannot serve visual tiles must not advertise a visual template");
        Assertions.assertFalse(entry.has("legend_url"),
                "no legend without the visual capability");
        Assertions.assertTrue(entry.has("data_tile_url_template"));
        Assertions.assertTrue(entry.has("data_manifest_url_template"));
    }

    @Test
    public void verifyPairIsDataOnlyEvenWhenDasReportsVisualExplicitly() {
        JsonNode product = multiVariableProduct("model_currents:ucur+vcur", "uuid-a", List.of("UCUR", "VCUR"));
        ((ObjectNode) product).put("visual", false);
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(List.of(product));
        when(dasTilerService.getManifest()).thenReturn(emptyManifest());

        JsonNode entry = getProducts("uuid-a").get(0);

        Assertions.assertEquals(List.of("data"), tileTypesOf(entry));
        // The `+` must survive as %2B here too — this template is built for a pair either way.
        Assertions.assertTrue(entry.get("data_tile_url_template").asText().contains("variable=ucur%2Bvcur"),
                "got: " + entry.get("data_tile_url_template").asText());
        Assertions.assertTrue(entry.get("data_manifest_url_template").asText().contains("variable=ucur%2Bvcur"),
                "got: " + entry.get("data_manifest_url_template").asText());
    }

    @Test
    public void verifyOldDasWithoutVisualFieldFallsBackToArity() {
        // The rolling deployment this service is released into: OGC first, DAS second, so for a
        // while every payload arrives without a `visual` field and must behave exactly as before.
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(
                        singleVariableProduct("model_sla:gsla", "uuid-a", "GSLA"),
                        multiVariableProduct("model_currents:ucur+vcur", "uuid-a", List.of("UCUR", "VCUR"))
                )
        );
        when(dasTilerService.getManifest()).thenReturn(manifestWith("model_sla:gsla"));

        JsonNode products = getProducts("uuid-a");

        Assertions.assertEquals(List.of("visual", "data"), tileTypesOf(products.get(0)),
                "a scalar from an old DAS keeps the legacy visual+data capability");
        Assertions.assertEquals(List.of("data"), tileTypesOf(products.get(1)),
                "a pair from an old DAS keeps the legacy data-only capability");
    }

    @Test
    public void verifyDataCapabilityStillFollowsArityNotTheVisualField() {
        // `visual` governs the visual capability only. Data tiles remain an arity question: the
        // shader packs one or two channels regardless of colourability.
        when(dasTilerService.productsForCollection("uuid-a")).thenReturn(
                List.of(scalarProductWithVisual("model_sla:wdir", "uuid-a", "WDIR", false))
        );
        when(dasTilerService.getManifest()).thenReturn(emptyManifest());

        JsonNode entry = getProducts("uuid-a").get(0);

        Assertions.assertTrue(tileTypesOf(entry).contains("data"),
                "visual: false must not remove the data capability");
    }

    private JsonNode getProducts(String collectionId) {
        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/" + collectionId + "/products", JsonNode.class
        );
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().get("products");
    }

    private List<String> tileTypesOf(JsonNode entry) {
        List<String> types = new ArrayList<>();
        entry.get("tile_types").forEach(node -> types.add(node.asText()));
        return types;
    }

    // --- Data-tile route: value-encoded PNG passthrough, floor-only validation, forwarded DAS errors ---

    @Test
    public void verifyDataTileReturnsImageWithCacheControl() {
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
    public void verifyDataTileRejectsUnencodedPlusInVariable() {

        // A raw '+' decodes to a space, so the product id would be 'model_sla:ucur vcur' — caught
        // here rather than forwarded to DAS as an unresolvable id.
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=model_sla&variable=ucur+vcur&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(response.getBody().getMessage().contains("%2B"),
                "the message must name the fix, got: " + response.getBody().getMessage());
        verify(dasTilerService, never()).getDataTile(anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void verifyDataTileUnknownProductIsForwardedToDas() {
        // DAS owns the product catalogue, so an unknown dataset is its answer to give.
        // Previously this 404'd locally from an Elasticsearch membership check that could
        // disagree with what DAS actually publishes.
        when(dasTilerService.getDataTile("wrong:gsla", "2024-01-01", 1, 0, 0))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "Unknown product: wrong:gsla"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                        + "?dataset=wrong&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Unknown product: wrong:gsla", response.getBody().getMessage());
        verify(dasTilerService).getDataTile("wrong:gsla", "2024-01-01", 1, 0, 0);
    }

    @Test
    public void verifyDataTileMirrorsUpstreamNotFound() {
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
        when(dasTilerService.getDataTile("model_sla:gsla", "2024-01-01", 1, 0, 0))
                .thenThrow(new DasUpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"));

        // The Accept headers real callers send — a map client (e.g. Mapbox) that names both image formats
        List<List<MediaType>> accepts = List.of(
                List.of(IMAGE_WEBP, MediaType.ALL),
                List.of(IMAGE_WEBP, MediaType.IMAGE_PNG, MediaType.ALL),
                List.of(MediaType.ALL));

        for (List<MediaType> accept : accepts) {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(accept);

            ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                    getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/1/0/0"
                            + "?dataset=model_sla&variable=gsla&datetime=2024-01-01",
                    HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(),
                    "upstream 503 must not degrade to 500 under Accept: " + MediaType.toString(accept));
            Assertions.assertEquals("Service Unavailable", response.getBody().getMessage(),
                    "upstream detail must reach the caller under Accept: " + MediaType.toString(accept));
        }
    }

    // --- Point route: decoded value(s) at a lat/lon, JSON body with query params ---

    @Test
    public void verifyDataPointReturnsJsonWithCacheControl() {
        ObjectNode pointBody = mapper.createObjectNode();
        pointBody.put("lat", -44.27813720703125);
        pointBody.put("lon", 132.0092315673828);
        when(dasTilerService.getPoint("model_sla:gsla", "2024-01-01", -44.27, 132.00))
                .thenReturn(new DasTilerService.DasJsonResult(pointBody, "public, max-age=31536000, immutable"));

        ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01&lat=-44.27&lon=132.00", JsonNode.class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().has("lat"));
        Assertions.assertEquals("public, max-age=31536000, immutable", response.getHeaders().getCacheControl());
    }

    @Test
    public void verifyDataPointRejectsMissingOrMalformedParams() {
        // missing dataset
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?variable=gsla&datetime=2024-01-01&lat=-44.27&lon=132.00", ErrorResponse.class).getStatusCode());
        // missing variable
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&datetime=2024-01-01&lat=-44.27&lon=132.00", ErrorResponse.class).getStatusCode());
        // datetime not YYYY-MM-DD
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-1-1&lat=-44.27&lon=132.00", ErrorResponse.class).getStatusCode());
    }

    @Test
    public void verifyDataPointRejectsMissingOrOutOfRangeLatLon() {
        // missing lat
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01&lon=132.00", ErrorResponse.class).getStatusCode());
        // missing lon
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01&lat=-44.27", ErrorResponse.class).getStatusCode());
        // lat out of range
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01&lat=91&lon=132.00", ErrorResponse.class).getStatusCode());
        // lon out of range
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=gsla&datetime=2024-01-01&lat=-44.27&lon=181", ErrorResponse.class).getStatusCode());
        verify(dasTilerService, never()).getPoint(anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    public void verifyDataPointRejectsUnencodedPlusInVariable() {
        // A raw '+' decodes to a space, so the product id would be 'model_sla:ucur vcur' — caught
        // here rather than forwarded to DAS as an unresolvable id.
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=model_sla&variable=ucur+vcur&datetime=2024-01-01&lat=-44.27&lon=132.00", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(response.getBody().getMessage().contains("%2B"),
                "the message must name the fix, got: " + response.getBody().getMessage());
        verify(dasTilerService, never()).getPoint(anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    public void verifyDataPointUnknownProductIsForwardedToDas() {
        // DAS owns the product catalogue, so an unknown dataset is its answer to give.
        when(dasTilerService.getPoint("wrong:gsla", "2024-01-01", -44.27, 132.00))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "Unknown product: wrong:gsla"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/point"
                        + "?dataset=wrong&variable=gsla&datetime=2024-01-01&lat=-44.27&lon=132.00", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Unknown product: wrong:gsla", response.getBody().getMessage());
        verify(dasTilerService).getPoint("wrong:gsla", "2024-01-01", -44.27, 132.00);
    }

    @Test
    public void verifyDataManifestReturnsJsonWithCacheControl() {
        ObjectNode manifestBody = mapper.createObjectNode();
        manifestBody.putArray("bounds").add(0).add(0).add(1).add(1);
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
        // an unencoded '+' in variable decodes to a space
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?dataset=model_sla&variable=ucur+vcur&datetime=2024-01-01", ErrorResponse.class).getStatusCode());
    }

    @Test
    public void verifyDataManifestUnknownProductIsForwardedToDas() {
        when(dasTilerService.getDataManifest("wrong:gsla", "2024-01-01"))
                .thenThrow(new DasUpstreamException(HttpStatus.NOT_FOUND, "Unknown product: wrong:gsla"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/collections/uuid-a/data_tiles/manifest"
                        + "?dataset=wrong&variable=gsla&datetime=2024-01-01", ErrorResponse.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Unknown product: wrong:gsla", response.getBody().getMessage());
        verify(dasTilerService).getDataManifest("wrong:gsla", "2024-01-01");
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
                getExternalBasePath() + "/tiles/legend?colormap=viridis", byte[].class
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertArrayEquals("legend-bytes".getBytes(), response.getBody());
    }

    @Test
    public void verifyColormapLegendOmittedColormapUsesDefault() {
        when(dasTilerService.getLegend(null, null, null, null, null))
                .thenReturn(new DasTilerService.DasTileResult(
                        "legend-bytes".getBytes(), "image/png", "public, max-age=31536000, immutable"));

        ResponseEntity<byte[]> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/legend", byte[].class
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
                .thenThrow(new DasUpstreamException(HttpStatus.BAD_REQUEST, "no such colormap"));

        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                getExternalBasePath() + "/tiles/legend?colormap=nosuch", ErrorResponse.class
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertNotNull(response.getBody(), "Body not null");
        Assertions.assertEquals("no such colormap", response.getBody().getMessage(),
                "Message carries the upstream reason");
        Assertions.assertNotNull(response.getBody().getTimestamp(), "Timestamp populated by the handler");
        Assertions.assertTrue(
                response.getBody().getDetails().contains("/tiles/legend"),
                "Details identify the failing request, got: " + response.getBody().getDetails());
    }
}
