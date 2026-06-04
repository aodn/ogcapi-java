package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import au.org.aodn.ogcapi.server.core.model.DatasetMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("DataAccessService")
public class DasService {

    @Autowired
    protected DASConfig dasConfig;

    @Autowired
    protected RestTemplate httpClient;

    private HttpEntity<?> httpEntity;

    @PostConstruct
    public void init() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret());
        headers.set("x-internal-das-header-secret", dasConfig.internal());
        httpEntity = new HttpEntity<>(headers);
    }

    /**
     * GET a feature-collection from the DAS, optionally bounded by start/end date. Only the date
     * query params that are non-null are added, so a null value is never passed to URI template
     * expansion (which would throw). Any path variables in {@code path} are supplied via
     * {@code pathVariables}.
     */
    private byte[] getFeatureCollection(String path, String start, String end, Map<String, String> pathVariables) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(dasConfig.host() + path);
        Map<String, String> params = new HashMap<>(pathVariables);

        if (start != null) {
            builder.queryParam("start_date", "{start_date}");
            params.put("start_date", start);
        }
        if (end != null) {
            builder.queryParam("end_date", "{end_date}");
            params.put("end_date", end);
        }

        String url = builder.encode().toUriString();
        return httpClient.exchange(url, HttpMethod.GET, httpEntity, byte[].class, params).getBody();
    }

    public byte[] getWaveBuoysBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy", start, end, Map.of());
    }

    public byte[] getWaveBuoysLatestAvailableDate() {
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host() + "/api/v1/das/data/feature-collection/wave-buoy/latest")
                .encode()
                .toUriString();

        return httpClient.exchange(waveBuoysUrlTemplate, HttpMethod.GET,httpEntity,byte[].class).getBody();
    }

    public byte[] getWaveBuoyDetailsBetweenDates(String startDateTime, String endDateTime, String buoy) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy/{buoy}", startDateTime, endDateTime, Map.of("buoy", buoy));
    }

    public byte[] getMooringsBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring", start, end, Map.of());
    }

    public byte[] getMooringsLatestAvailableDate() {
        String mooringsUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host() + "/api/v1/das/data/feature-collection/mooring/latest")
                .encode()
                .toUriString();

        return httpClient.exchange(mooringsUrlTemplate, HttpMethod.GET,httpEntity,byte[].class).getBody();
    }

    public byte[] getMooringDetailsBetweenDates(String startDateTime, String endDateTime, String mooring) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring/{mooring}", startDateTime, endDateTime, Map.of("mooring", mooring));
    }

    /**
     * Call the data-access-service cloud-optimised size estimate endpoint.
     *
     * POST /api/v1/das/data/{uuid}/estimate_size with a JSON body matching
     * EstimateSizeRequest. The endpoint is multi-key and aggregates server-side:
     * a null/"*" keys list means "all keys of the uuid" (same as the batch
     * download). Returns the raw JSON response body so the SSE layer can forward
     * it to the frontend unchanged.
     */
    public String estimateCloudOptimisedDownloadSize(
            String uuid,
            List<String> keys,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> columns,
            String outputFormat) {

        String url = UriComponentsBuilder.fromUriString(dasConfig.host() + "/api/v1/das/data/{uuid}/estimate_size")
                .encode()
                .toUriString();

        // Body mirrors EstimateSizeRequest. Send the raw frontend date strings
        // (or "non-specified" when null) so data-access-service applies the same
        // resolve/supply/trim chain the batch download uses.
        Map<String, Object> body = new HashMap<>();
        body.put("keys", keys); // null => all keys of the uuid
        body.put("start_date", startDate != null ? startDate : "non-specified");
        body.put("end_date", endDate != null ? endDate : "non-specified");
        body.put("output_format", outputFormat);
        // multi_polygon is accepted as a GeoJSON object or string; forward as-is.
        if (multiPolygon != null) {
            body.put("multi_polygon", multiPolygon);
        }
        // columns is not sent today (frontend doesn't subset columns yet, and the
        // batch download grabs all variables), keeping the estimate aligned.
        if (columns != null) {
            body.put("columns", columns);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret());
        headers.set("x-internal-das-header-secret", dasConfig.internal());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("uuid", uuid);

        return httpClient.exchange(url, HttpMethod.POST, entity, String.class, uriVars).getBody();
    }

    public ResponseEntity<DatasetMetadata> getDatasetMetadata(String datasetId) {
        ResponseEntity<DatasetMetadata> response = httpClient.exchange(
                dasConfig.host() + "/api/v1/das/metadata/" + datasetId,
                HttpMethod.GET,
                httpEntity,
                DatasetMetadata.class
        );
        // We need to do this so that the response is closed
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
