package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.model.DatasetMetadata;
import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import au.org.aodn.ogcapi.server.core.util.DasSseFrames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

@Service("DataAccessService")
public class DasService implements ApplicationInfo {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_FRAME =
            new ParameterizedTypeReference<>() {
            };

    private static final int MAX_UPSTREAM_DETAIL_CHARS = 200;

    protected final DasProperties dasProperties;
    protected final RestTemplate httpClient;
    protected final WebClient sseHttpClient;
    protected final ObjectMapper objectMapper;
    protected final Map<String, Map<?, ?>> appInfo;

    public DasService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_REST_TEMPLATE) RestTemplate httpClient,
            @Qualifier(Config.DAS_SSE_WEB_CLIENT) WebClient sseHttpClient,
            ObjectMapper objectMapper) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
        this.sseHttpClient = sseHttpClient;
        this.objectMapper = objectMapper;
        this.appInfo = queryInfo(httpClient, dasProperties.host(), dasProperties.infoPath());
    }

    /**
     * GET a feature-collection from DAS, optionally bounded by start/end date. Only non-null dates
     * are added as query params, because expanding a null URI template variable would throw. Any
     * path variables in path come from pathVariables.
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
     * Ask DAS for a cloud-optimised size estimate and return the estimate JSON unchanged, for the
     * SSE layer to forward on. The parameters map is the same subset request the download job
     * sends (see SubsetParametersUtils), so DAS treats both alike. Three things to know:
     * 1. DAS answers over SSE: heartbeats while it computes, then the estimate as a final event.
     * The stream returns 200 as soon as it opens, so a failed estimate arrives as an error event
     * that DasSseFrames turns into an exception. Only failures before the stream opens (auth, API
     * not ready) are HTTP errors, and onStatus rewrites those to hide the DAS host.
     * 2. The response is not buffered. onHeartbeat runs on this thread for each heartbeat, and
     * callers write to their own SSE client there, which is the only way to notice that client
     * has gone. The IOException it throws unwinds this call, and leaving the try-with-resources
     * cancels the Flux, closing the connection so DAS stops the estimate. That cancel only
     * reaches the socket because of CancelPropagatingJdkConnector.
     * 3. sseIdleTimeout is the gap allowed between frames, not a limit on the whole call. A slow
     * estimate is fine while DAS keeps heartbeating; a silent DAS is given up on.
     */
    public String estimateCloudOptimisedDownloadSize(String uuid,
                                                     Map<String, String> parameters,
                                                     DasSseFrames.FrameCallback onHeartbeat) {

        Flux<ServerSentEvent<String>> frames = sseHttpClient.post()
                .uri("/api/v1/das/data/{uuid}/estimate_size", uuid)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(parameters)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new RuntimeException(describe(response.statusCode(), body))))
                .bodyToFlux(SSE_FRAME)
                .timeout(dasProperties.sseIdleTimeout());

        boolean sawFrame = false;

        // Closing the stream cancels the Flux, and that is what closes the connection to DAS.
        try (Stream<ServerSentEvent<String>> stream = frames.toStream()) {
            for (Iterator<ServerSentEvent<String>> it = stream.iterator(); it.hasNext(); ) {
                sawFrame = true;
                String payload = DasSseFrames.readTerminalFrame(objectMapper, it.next());
                if (payload != null) {
                    return payload;
                }
                onHeartbeat.onFrame();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        throw new RuntimeException(sawFrame ?
                "data-access-service stream ended without a result or error event" :
                "Empty response from data-access-service");
    }

    /**
     * Describe a failure that arrived before the stream opened.
     */
    private String describe(HttpStatusCode status, String body) {
        String reason = status instanceof HttpStatus known ? " " + known.getReasonPhrase() : "";
        String failure = "data-access-service returned " + status.value() + reason;
        String detail = reasonFrom(body);
        return detail.isEmpty() ? failure : failure + ": " + detail;
    }

    /**
     * Pull the reason out of an error body.
     */
    private String reasonFrom(String body) {
        String flattened = body.replaceAll("\\s+", " ").trim();
        if (flattened.isEmpty()) {
            return "";
        }

        try {
            JsonNode detail = objectMapper.readTree(flattened).get("detail");
            if (detail != null && !detail.isNull()) {
                return truncate(detail.isTextual() ? detail.asText() : detail.toString());
            }
        } catch (Exception ignored) {
            // Not JSON, so not DAS speaking. Fall through and quote what little is useful.
        }

        return flattened.startsWith("<") ? "" : truncate(flattened);
    }

    private static String truncate(String detail) {
        return detail.length() <= MAX_UPSTREAM_DETAIL_CHARS ?
                detail :
                detail.substring(0, MAX_UPSTREAM_DETAIL_CHARS) + "...";
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
