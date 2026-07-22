package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
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
import java.util.Set;

/**
 * Calls DAS's tiler endpoints (visual-tile images, product/manifest listing, colormaps/legend)
 * server-to-server, attaching the DAS API key. Sibling of {@link DasService} rather than an
 * extension of it: same client, but this half of the DAS surface returns binary image bytes and
 * maps upstream failures onto tile-shaped responses.
 * <p>
 * Product ids contain {@code :}/{@code +} (e.g. {@code model_sea_level_anomaly_gridded_realtime:gsla}),
 * so URL building always goes through {@link UriComponentsBuilder} path-variable expansion
 * (mirrors {@link DasService#getWaveBuoyDetailsBetweenDates}) rather than string concatenation.
 */
@Slf4j
@Service("DasTilerService")
public class DasTilerService {

    private static final String VISUAL_TILES_BASE = "/api/v1/das/tiler/visual_tiles";

    protected final DasProperties dasProperties;

    /**
     * The shared DAS client — short timeouts, and it attaches the DAS API key to every request
     */
    protected final RestTemplate httpClient;

    private final ObjectMapper mapper;

    /**
     * Self-reference through the caching proxy. {@code @Cacheable} is a proxy-based concern: a
     * plain {@code this.getProducts()} call from inside this class bypasses the proxy entirely
     * and silently never caches. Internal callers of a cached method must go through
     * {@code self}, not {@code this}.
     */
    private DasTilerService self = this;

    public DasTilerService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_REST_TEMPLATE) RestTemplate httpClient,
            ObjectMapper mapper) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Autowired
    public void setSelf(@Lazy DasTilerService self) {
        this.self = self;
    }

    /**
     * Wraps a DAS binary response (visual tile / legend image) with the upstream headers this
     * service forwards verbatim.
     */
    public record DasTileResult(byte[] body, String contentType, String cacheControl) {
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
     * Cached: {@link #isProductInCollection} runs on the visual-tile hot path, so without this
     * every tile request costs a second DAS round-trip on top of the tile fetch itself. The list
     * is small and slow-changing; see {@link CacheConfig#CACHE_TILER_PRODUCTS} for the TTL.
     */
    @Cacheable(CacheConfig.CACHE_TILER_PRODUCTS)
    public List<JsonNode> getProducts() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/products";
        try {
            JsonNode body = httpClient.getForObject(url, JsonNode.class);
            List<JsonNode> result = new ArrayList<>();
            if (body != null && body.isArray()) {
                body.forEach(result::add);
            }
            // The cached value is shared across request threads, so hand out an immutable view
            return List.copyOf(result);
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
        for (JsonNode product : self.getProducts()) {
            if (collectionId.equals(product.path("metadata_uuid").asText(null))) {
                result.add(product);
            }
        }
        return result;
    }

    public boolean isProductInCollection(String collectionId, String productId) {
        for (JsonNode product : productsForCollection(collectionId)) {
            if (productId.equals(product.path("id").asText(null))) {
                return true;
            }
        }
        return false;
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
