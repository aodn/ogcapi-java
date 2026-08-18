package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.model.DatasetMetadata;
import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import au.org.aodn.ogcapi.server.core.util.SseResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("DataAccessService")
public class DasService implements ApplicationInfo {

    protected final DasProperties dasProperties;
    protected final RestTemplate httpClient;
    protected final RestTemplate sseHttpClient;
    protected final ObjectMapper objectMapper;
    protected final Map<String, Map<?, ?>> appInfo;

    public DasService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_REST_TEMPLATE) RestTemplate httpClient,
            @Qualifier(Config.DAS_SSE_REST_TEMPLATE) RestTemplate sseHttpClient,
            ObjectMapper objectMapper) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
        this.sseHttpClient = sseHttpClient;
        this.objectMapper = objectMapper;
        this.appInfo = queryInfo(httpClient, dasProperties.host(), dasProperties.infoPath());
    }

    /**
     * GET a feature-collection from the DAS, optionally bounded by start/end date. Only the date
     * query params that are non-null are added, so a null value is never passed to URI template
     * expansion (which would throw). Any path variables in {@code path} are supplied via
     * {@code pathVariables}.
     */
    private ResponseEntity<byte[]> getFeatureCollection(String path, String start, String end, Map<String, String> pathVariables) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(dasProperties.host() + path);
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
        return httpClient.getForEntity(url, byte[].class, params);
    }

    public ResponseEntity<byte[]> getWaveBuoysBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy", start, end, Map.of());
    }

    public ResponseEntity<byte[]> getWaveBuoysLatestAvailableDate() {
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/feature-collection/wave-buoy/latest")
                .encode()
                .toUriString();

        return httpClient.getForEntity(waveBuoysUrlTemplate, byte[].class);
    }

    public ResponseEntity<byte[]> getWaveBuoyDetailsBetweenDates(String startDateTime, String endDateTime, String buoy) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy/{buoy}", startDateTime, endDateTime, Map.of("buoy", buoy));
    }

    public ResponseEntity<byte[]> getMooringsBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring", start, end, Map.of());
    }

    public ResponseEntity<byte[]> getMooringsLatestAvailableDate() {
        String mooringsUrlTemplate = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/feature-collection/mooring/latest")
                .encode()
                .toUriString();

        return httpClient.getForEntity(mooringsUrlTemplate, byte[].class);
    }

    public ResponseEntity<byte[]> getMooringDetailsBetweenDates(String startDateTime, String endDateTime, String mooring) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring/{mooring}", startDateTime, endDateTime, Map.of("mooring", mooring));
    }

    /**
     * Call the data-access-service cloud-optimised size estimate endpoint and return the
     * estimate JSON, so the SSE layer can forward it to the frontend unchanged. The parameters
     * map is the same batch-style subset request the download job submits (see
     * SubsetParametersUtils), so DAS treats the estimate and the download identically.
     * Two things to know:
     * 1. DAS streams this endpoint over SSE. It heartbeats while computing and sends the
     * estimate in a final event, so frames are read as they arrive and unwrapped by
     * SseResponseParser. The stream returns 200 as soon as it opens, so a failed estimate
     * arrives as an error event, not an error status, and the parser turns it back into an
     * exception. Only failures before the stream starts (auth, API not ready) are HTTP errors.
     * 2. The response is deliberately not buffered. onHeartbeat runs on this thread once per
     * DAS heartbeat, and callers use it to write to their own SSE client. That write is the
     * only way to notice the client has disconnected, and since it runs on the thread blocked
     * on DAS, the IOException it throws unwinds this call and closes the connection to DAS.
     * DAS then stops the estimate at its next cancellation checkpoint.
     */
    public String estimateCloudOptimisedDownloadSize(String uuid,
                                                     Map<String, String> parameters,
                                                     SseResponseParser.FrameCallback onHeartbeat) {

        String url = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/{uuid}/estimate_size")
                .encode()
                .toUriString();

        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("uuid", uuid);

        RequestCallback requestCallback = request -> {
            HttpHeaders headers = request.getHeaders();
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
            // Jackson's XML converter also claims Map bodies, so the content type is explicit.
            headers.setContentType(MediaType.APPLICATION_JSON);
            objectMapper.writeValue(request.getBody(), parameters);
        };

        ResponseExtractor<String> responseExtractor = response -> {
            // Closing the reader closes the response body, which cancels the exchange: on the
            // disconnect path DAS is notified here, before RestTemplate's own cleanup runs.
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                return SseResponseParser.extractResultData(objectMapper, reader, onHeartbeat);
            }
        };

        return sseHttpClient.execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVars);
    }

    public ResponseEntity<DatasetMetadata> getDatasetMetadata(String datasetId) {
        ResponseEntity<DatasetMetadata> response = httpClient.getForEntity(
                dasProperties.host() + "/api/v1/das/metadata/" + datasetId,
                DatasetMetadata.class
        );
        // We need to do this so that the response is closed
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }

    @Override
    public String getName() {
        Object name = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("name", null);
        return name != null ? name.toString() : null;
    }

    @Override
    public String getVersion() {
        Object version = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("version", null);
        return version != null ? version.toString() : null;
    }

    @Override
    public String getDescription() {
        Object description = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("description", null);
        return description != null ? description.toString() : null;
    }
}
