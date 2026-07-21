package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.util.DasUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls DAS's tiler endpoints (visual-tile images, product/manifest listing, colormaps/legend)
 * server-to-server, attaching the DAS API key. Sibling of {@link DasService} rather than an
 * extension of it: DasService's shared {@code HttpEntity} is JSON-Accept-oriented, whereas most
 * of what this service fetches is binary image bytes.
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
     * Qualified onto the dedicated short-timeout client rather than the application-wide
     * RestTemplate, whose 20-minute timeouts are sized for large WFS/WMS downloads.
     */
    protected final RestTemplate httpClient;

    private final HttpEntity<?> httpEntity;

    public DasTilerService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_TILER_REST_TEMPLATE) RestTemplate httpClient) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
        httpEntity = new HttpEntity<>(DasUtils.authHeaders(dasProperties));
    }

    /**
     * Wraps a DAS binary response (visual tile / legend image) with the upstream headers this
     * service forwards verbatim.
     */
    public record DasTileResult(byte[] body, String contentType, String cacheControl) {
    }

    /**
     * Fetches one visual tile from DAS. Nothing in this service is cached — it is a pure
     * pass-through, by choice, for this stage.
     * <p>
     * An earlier revision cached tile bytes in EhCache (heap + disk, 24 h). It was dropped
     * because this is the wrong layer to cache at: DAS already returns a one-year
     * {@code immutable} {@code Cache-Control} that this service forwards verbatim, so browsers
     * and (once it fronts ogcapi) CloudFront are what should absorb repeat reads. An origin-side
     * copy on top of that mainly added a staleness window nothing could invalidate — there is no
     * renderer-version cache-buster, so a DAS re-render stayed pinned until the entry aged out
     * or the pod restarted.
     * <p>
     * Load consequence, worth re-checking before this route is public: DAS runs with
     * {@code cache_backend: "none"}, so it memoises nothing between requests and every call here
     * is a full S3 Zarr read plus render on its side. Its {@code Deduper} only coalesces
     * genuinely concurrent identical renders. Until CloudFront fronts this route, all repeat
     * load lands on DAS.
     */
    public DasTileResult getVisualTile(String productId, String date, int z, int x, int y, String ext,
                                       String colormap, String rescale) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasProperties.host() + VISUAL_TILES_BASE + "/{product}/{date}/{z}/{x}/{y}.{ext}");
        Map<String, Object> params = new HashMap<>();
        params.put("product", productId);
        params.put("date", date);
        params.put("z", z);
        params.put("x", x);
        params.put("y", y);
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

    public List<JsonNode> getProducts() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/products";
        try {
            JsonNode body = httpClient.exchange(url, HttpMethod.GET, httpEntity, JsonNode.class).getBody();
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
            return httpClient.exchange(url, HttpMethod.GET, httpEntity, JsonNode.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public JsonNode getColormaps() {
        String url = dasProperties.host() + VISUAL_TILES_BASE + "/colormaps";
        try {
            return httpClient.exchange(url, HttpMethod.GET, httpEntity, JsonNode.class).getBody();
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

    /**
     * Products from the DAS {@code /products} listing whose {@code metadata_uuid} matches this
     * collection. Never parses the product id — matching is purely on the field DAS added for
     * exactly this purpose.
     * <p>
     * Note this issues a fresh {@code /products} request per call, and
     * {@link #isProductInCollection} sits on the tile route's hot path — so each visual-tile
     * request currently costs two sequential DAS round trips (membership check, then the tile).
     */
    public List<JsonNode> productsForCollection(String collectionId) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode product : getProducts()) {
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
            ResponseEntity<byte[]> response = httpClient.exchange(url, HttpMethod.GET, httpEntity, byte[].class, params);
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

    private DasUpstreamException mapUpstreamError(HttpStatusCodeException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            // Misconfigured X-API-KEY on our side, never the caller's fault.
            log.error("DAS rejected request as unauthorized — check data-access-service.secret configuration", e);
            return DasUpstreamException.withDetail(HttpStatus.BAD_GATEWAY, "Upstream authentication error");
        }
        if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.NOT_FOUND
                || status == HttpStatus.UNPROCESSABLE_ENTITY || status == HttpStatus.TOO_MANY_REQUESTS
                || status == HttpStatus.SERVICE_UNAVAILABLE) {
            MediaType contentType = e.getResponseHeaders() != null ? e.getResponseHeaders().getContentType() : null;
            return new DasUpstreamException(
                    status,
                    e.getResponseBodyAsByteArray(),
                    contentType != null ? contentType : MediaType.APPLICATION_JSON
            );
        }
        log.error("Unexpected status {} from DAS", status, e);
        return DasUpstreamException.withDetail(HttpStatus.BAD_GATEWAY, "Upstream error");
    }

    private DasUpstreamException mapNetworkError(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return DasUpstreamException.withDetail(HttpStatus.GATEWAY_TIMEOUT, "DAS request timed out");
        }
        log.error("Network failure calling DAS", e);
        return DasUpstreamException.withDetail(HttpStatus.BAD_GATEWAY, "Upstream network failure");
    }
}
