package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.stac.model.StacCollectionModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calls DAS's tiler endpoints (visual- and data-tile images, the per-date data manifest,
 * product/manifest listing, colormaps/legend) server-to-server, attaching the DAS API key.
 * Sibling of {@link DasService} rather than an extension of it: same client, but this half of the
 * DAS surface returns binary image bytes and maps upstream failures onto tile-shaped responses.
 * <p>
 * Product ids contain {@code :}/{@code +} (e.g. {@code model_sea_level_anomaly_gridded_realtime:gsla}),
 * so URL building always goes through {@link UriComponentsBuilder} path-variable expansion
 * (mirrors {@link DasService#getWaveBuoyDetailsBetweenDates}) rather than string concatenation.
 */
@Slf4j
@Service("DasTilerService")
public class DasTilerService {

    private static final String VISUAL_TILES_BASE = "/api/v1/das/tiler/visual_tiles";
    private static final String DATA_TILES_BASE = "/api/v1/das/tiler/data_tiles";

    protected final DasProperties dasProperties;
    protected final Search search;

    /**
     * The shared DAS client — short timeouts, and it attaches the DAS API key to every request
     */
    protected final RestTemplate httpClient;

    private final ObjectMapper mapper;

    public DasTilerService(
            DasProperties dasProperties, Search search,
            @Qualifier(Config.DAS_REST_TEMPLATE) RestTemplate httpClient,
            ObjectMapper mapper) {
        this.dasProperties = dasProperties;
        this.search = search;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Wraps a DAS binary response (visual tile / legend image) with the upstream headers this
     * service forwards verbatim.
     */
    public record DasTileResult(byte[] body, String contentType, String cacheControl) {
    }

    /**
     * Wraps a DAS JSON response (the per-date data-tile manifest) together with the immutable
     * {@code Cache-Control} this service forwards verbatim.
     */
    public record DasJsonResult(JsonNode body, String cacheControl) {
    }

    /**
     * Fetches one slippy-map tile. {@code zoom}/{@code tileX}/{@code tileY} are XYZ tile
     * coordinates (not OGC row/column): x runs west→east, y runs north→south, and both are
     * bounded by 2^zoom - 1.
     */
    public DasTileResult getVisualTile(String productId, String date, int zoom, int tileX, int tileY,
                                       String ext, String colormap, String rescale) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasProperties.host() + VISUAL_TILES_BASE + "/{product}/{date}/{z}/{x}/{y}.{ext}");
        Map<String, Object> params = new HashMap<>();
        params.put("product", productId);
        params.put("date", date);
        params.put("z", zoom);
        params.put("x", tileX);
        params.put("y", tileY);
        params.put("ext", ext);

        if (colormap != null) {
            builder.queryParam("colormap", "{colormap}");
            params.put("colormap", colormap);
        }
        if (rescale != null) {
            builder.queryParam("rescale", "{rescale}");
            params.put("rescale", rescale);
        }

        return exchangeForImage(builder, params, "image/" + ext);
    }

    /**
     * Fetches one value-encoded data tile. {@code lod} is a product-local level of detail
     * ({@code 1} is the coarsest, at most 4 levels) and {@code x}/{@code y} are chunk column/row
     * within that LOD's grid — not slippy-map XYZ. The bytes are an opaque DAS&lt;-&gt;shader ABI:
     * this service never parses or transforms them, only forwards them with their upstream headers.
     */
    public DasTileResult getDataTile(String productId, String date, int lod, int x, int y) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasProperties.host() + DATA_TILES_BASE + "/{product}/{date}/{z}/{x}/{y}.png");
        Map<String, Object> params = new HashMap<>();
        params.put("product", productId);
        params.put("date", date);
        params.put("z", lod);
        params.put("x", x);
        params.put("y", y);

        return exchangeForImage(builder, params, "image/png");
    }

    /**
     * Fetches the per-date data-tile manifest (decode ranges, bounds, LOD geometry) needed to
     * decode the data tiles. Unlike the plain JSON getters this expands the product id (which
     * contains {@code :}/{@code +}) as a path variable and forwards the immutable
     * {@code Cache-Control}.
     */
    public DasJsonResult getDataManifest(String productId, String date) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasProperties.host() + DATA_TILES_BASE + "/{product}/{date}/manifest.json");
        Map<String, Object> params = new HashMap<>();
        params.put("product", productId);
        params.put("date", date);

        return exchangeForJson(builder, params);
    }

    public List<JsonNode> getProducts() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/products";
        try {
            JsonNode body = httpClient.getForObject(url, JsonNode.class);
            List<JsonNode> result = new ArrayList<>();
            if (body != null && body.isArray()) {
                body.forEach(result::add);
            }
            return result;
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public JsonNode getManifest() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/manifest";
        try {
            return httpClient.getForObject(url, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public JsonNode getColormaps() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/colormaps";
        try {
            return httpClient.getForObject(url, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public DasTileResult getLegend(String name, String rescale, Integer width, Integer height,
                                    String orientation) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasProperties.host() + VISUAL_TILES_BASE + "/colormaps/{name}/legend");
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);

        if (rescale != null) {
            builder.queryParam("rescale", "{rescale}");
            params.put("rescale", rescale);
        }
        if (width != null) {
            builder.queryParam("width", "{width}");
            params.put("width", width);
        }
        if (height != null) {
            builder.queryParam("height", "{height}");
            params.put("height", height);
        }
        if (orientation != null) {
            builder.queryParam("orientation", "{orientation}");
            params.put("orientation", orientation);
        }

        return exchangeForImage(builder, params, "image/png");
    }

    public List<JsonNode> productsForCollection(String collectionId) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode product : getProducts()) {
            if (collectionId.equals(product.path("metadata_uuid").asText(null))) {
                result.add(product);
            }
        }
        return result;
    }

    public boolean isDatasetInCollection(String collectionId, String dataset) {
        var result = search.searchCollections(collectionId);
        if (result == null || result.getCollections() == null) {
            return false;
        }
        return result.getCollections().stream()
                .map(StacCollectionModel::getAssets)
                .filter(Objects::nonNull)
                .flatMap(assets -> assets.keySet().stream())
                .map(key -> key.contains(".") ? key.substring(0, key.indexOf('.')) : key)
                .anyMatch(dataset::equals);
    }

    private DasTileResult exchangeForImage(UriComponentsBuilder builder, Map<String, Object> params, String fallbackContentType) {
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<byte[]> response = httpClient.getForEntity(url, byte[].class, params);
            HttpHeaders headers = response.getHeaders();
            MediaType contentType = headers.getContentType();
            return new DasTileResult(
                    response.getBody(),
                    contentType != null ? contentType.toString() : fallbackContentType,
                    headers.getFirst(HttpHeaders.CACHE_CONTROL)
            );
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    /**
     * Like {@link #exchangeForImage} but for a JSON body: keeps path-variable expansion (product
     * ids carry {@code :}/{@code +}) and forwards the immutable {@code Cache-Control}, which the
     * plain JSON getters discard.
     */
    private DasJsonResult exchangeForJson(UriComponentsBuilder builder, Map<String, Object> params) {
        String url = builder.encode().toUriString();
        try {
            ResponseEntity<JsonNode> response = httpClient.getForEntity(url, JsonNode.class, params);
            HttpHeaders headers = response.getHeaders();
            return new DasJsonResult(response.getBody(), headers.getFirst(HttpHeaders.CACHE_CONTROL));
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    private static final Set<HttpStatus> MIRRORED_STATUSES = Set.of(
            HttpStatus.BAD_REQUEST,
            HttpStatus.NOT_FOUND,
            HttpStatus.UNPROCESSABLE_ENTITY,
            HttpStatus.TOO_MANY_REQUESTS,
            HttpStatus.SERVICE_UNAVAILABLE);

    private static final String UPSTREAM_FAILURE_MESSAGE = "Tile service is temporarily unavailable";

    private DasUpstreamException mapUpstreamError(HttpStatusCodeException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

        if (status != null && MIRRORED_STATUSES.contains(status)) {
            return new DasUpstreamException(status, upstreamDetail(e, status));
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.error("DAS rejected request as unauthorized — check data-access-service.secret configuration", e);
        } else {
            log.error("Unexpected status {} from DAS", status, e);
        }
        return new DasUpstreamException(HttpStatus.BAD_GATEWAY, UPSTREAM_FAILURE_MESSAGE);
    }

    private String upstreamDetail(HttpStatusCodeException e, HttpStatus status) {
        try {
            JsonNode body = mapper.readTree(e.getResponseBodyAsByteArray());
            String detail = body.path("detail").asText(null);
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        } catch (IOException ignored) {
        }
        return status.getReasonPhrase();
    }

    private DasUpstreamException mapNetworkError(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return new DasUpstreamException(HttpStatus.GATEWAY_TIMEOUT, "Tile service did not respond in time");
        }
        log.error("Network failure calling DAS", e);
        return new DasUpstreamException(HttpStatus.BAD_GATEWAY, UPSTREAM_FAILURE_MESSAGE);
    }
}
