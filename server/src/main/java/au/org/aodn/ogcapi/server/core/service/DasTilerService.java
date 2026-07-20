package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import au.org.aodn.ogcapi.server.core.exception.DasUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
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

import java.io.Serializable;
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

    @Autowired
    protected DASConfig dasConfig;

    @Autowired
    protected RestTemplate httpClient;

    /**
     * Self-reference through the CGLIB caching proxy. {@code @Cacheable} is a proxy-based
     * concern: a plain {@code this.getProducts()}/{@code this.getManifest()} call from within
     * this same class bypasses the proxy entirely and silently never caches. Calls to those
     * methods from elsewhere in this class must go through {@code self}, not {@code this}.
     * {@code @Lazy} avoids a circular-dependency failure at construction time.
     */
    @Autowired
    @Lazy
    protected DasTilerService self;

    private HttpEntity<?> httpEntity;

    @PostConstruct
    public void init() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", dasConfig.secret());
        if (dasConfig.internal() != null) {
            headers.set("x-internal-das-header-secret", dasConfig.internal());
        }
        httpEntity = new HttpEntity<>(headers);
    }

    /**
     * Wraps a DAS binary response (visual tile / legend image) with the upstream headers this
     * service forwards verbatim. A plain class rather than a record: EhCache's tiered-store
     * copier reflects on fields via {@code Unsafe.objectFieldOffset}, which throws
     * "can't get field offset on a record class" for record types — so this must stay a normal
     * Serializable class to be cacheable in {@link CacheConfig#CACHE_TILER_TILE}.
     */
    public static final class DasTileResult implements Serializable {
        private final byte[] body;
        private final String contentType;
        private final String cacheControl;

        public DasTileResult(byte[] body, String contentType, String cacheControl) {
            this.body = body;
            this.contentType = contentType;
            this.cacheControl = cacheControl;
        }

        public byte[] body() {
            return body;
        }

        public String contentType() {
            return contentType;
        }

        public String cacheControl() {
            return cacheControl;
        }
    }

    /**
     * Fetches one visual tile from DAS. If {@code cv} is supplied it is checked against the
     * cached manifest's {@code cache_version} first — a mismatch means the caller's URL is
     * stale under the immutable {@code Cache-Control} contract, so this throws 410 rather than
     * serving (possibly wrong) bytes. DAS itself ignores {@code cv} — it is only forwarded so
     * browser/CDN/EhCache cache keys vary with it.
     */
    @Cacheable(CacheConfig.CACHE_TILER_TILE)
    public DasTileResult getVisualTile(String productId, String date, int z, int x, int y, String ext,
                                        String colormap, String rescale, String cv) {
        if (cv != null) {
            JsonNode manifest = self.getManifest();
            String currentCv = manifest != null ? manifest.path("cache_version").asText(null) : null;
            if (currentCv != null && !cv.equals(currentCv)) {
                throw DasUpstreamException.withDetail(HttpStatus.GONE, "cache_version '" + cv + "' is stale");
            }
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasConfig.host() + VISUAL_TILES_BASE + "/{product}/{date}/{z}/{x}/{y}.{ext}");
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

    @Cacheable(CacheConfig.CACHE_TILER_PRODUCTS)
    public List<JsonNode> getProducts() {
        String url = dasConfig.host() + VISUAL_TILES_BASE + "/products";
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

    @Cacheable(CacheConfig.CACHE_TILER_MANIFEST)
    public JsonNode getManifest() {
        String url = dasConfig.host() + VISUAL_TILES_BASE + "/manifest";
        try {
            return httpClient.exchange(url, HttpMethod.GET, httpEntity, JsonNode.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public JsonNode getColormaps() {
        String url = dasConfig.host() + VISUAL_TILES_BASE + "/colormaps";
        try {
            return httpClient.exchange(url, HttpMethod.GET, httpEntity, JsonNode.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw mapUpstreamError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    public DasTileResult getLegend(String name, String rescale, Integer width, Integer height,
                                    String orientation, String cv) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(dasConfig.host() + VISUAL_TILES_BASE + "/colormaps/{name}/legend");
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
     * Products (from the cached {@code /products} listing) whose {@code metadata_uuid} matches
     * this collection. Never parses the product id — matching is purely on the field DAS added
     * for exactly this purpose.
     */
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
