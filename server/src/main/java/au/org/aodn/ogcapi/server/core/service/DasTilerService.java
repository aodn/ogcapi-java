package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import au.org.aodn.ogcapi.server.core.util.DasUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** Used only to read the {@code detail} field back out of a DAS error body. */
    private final ObjectMapper mapper;

    public DasTilerService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_TILER_REST_TEMPLATE) RestTemplate httpClient,
            ObjectMapper mapper) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
        this.mapper = mapper;
        httpEntity = new HttpEntity<>(DasUtils.authHeaders(dasProperties));
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
