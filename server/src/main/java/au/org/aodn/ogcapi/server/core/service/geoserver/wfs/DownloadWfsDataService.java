package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.model.enumeration.SseEventName;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DownloadWfsDataService {
    protected final WfsServer wfsServer;
    protected final RestTemplate restTemplate;
    protected final HttpEntity<?> pretendUserEntity;
    protected final int chunkSize;
    protected final ObjectMapper objectMapper;
    protected static final int SAMPLES_SIZE = 500;    // A not too small sample for download size estimation

    @Autowired
    @Lazy
    protected DownloadWfsDataService self;

    public DownloadWfsDataService(
            WfsServer wfsServer,
            RestTemplate restTemplate,
            HttpEntity<?> pretendUserEntity,
            int chunkSize,
            ObjectMapper objectMapper
    ) {
        this.wfsServer = wfsServer;
        this.pretendUserEntity = pretendUserEntity;
        this.chunkSize = chunkSize;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Does collection lookup, WFS validation, field retrieval, and URL building
     */
    public String prepareWfsRequestUrl(
            String uuid,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> fields,
            String layerName,
            String outputFormat,
            long maxRecordCount,
            boolean estimateSizeOnly) {


        // Get WFS server URL and field model for the given UUID and layer name
        Optional<String> featureServerUrl = wfsServer.getFeatureServerUrl(uuid, layerName);

        // Get the wfs fields to build the CQL filter
        if (featureServerUrl.isPresent()) {
            String wfsServerUrl = featureServerUrl.get();

            WfsServer.WfsFeatureRequest featureRequest = WfsServer.WfsFeatureRequest.builder()
                    .server(wfsServerUrl)
                    .layerName(layerName)
                    .datetime(DatetimeUtils.formatOGCDateTime(startDate, endDate))
                    .multiPolygon(multiPolygon)
                    .build();

            // Build final WFS request URL
            String wfsRequestUrl = wfsServer.createWfsRequestUrl(
                    wfsServerUrl,
                    layerName,
                    fields,
                    wfsServer.buildCqlFilter(uuid, featureRequest),
                    outputFormat,
                    maxRecordCount,
                    estimateSizeOnly);

            log.info("Prepared WFS request URL: {}", wfsRequestUrl);
            return wfsRequestUrl;
        } else {
            throw new IllegalArgumentException("No WFS server URL found for the given UUID and layer name");
        }
    }

    /**
     * Unfiltered total feature count for a layer
     * Cached per (uuid, layerName)
     */
    @Cacheable(CacheConfig.DOWNLOADABLE_SIZE)
    public BigInteger getUnfilteredRecordCount(String uuid, String layerName) {
        String countUrl = prepareWfsRequestUrl(
                uuid, null, null, null, null, layerName, "application/json", 1L, false
        );

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(countUrl, HttpMethod.GET, pretendUserEntity, String.class);
        } catch (RestClientException e) {
            log.error("WFS record count request failed for {}/{} against server url {}", uuid, layerName, countUrl, e);
            throw e;
        }
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (!root.has("totalFeatures")) {
                    throw new RuntimeException("GeoServer GeoJSON response missing totalFeatures field");
                }
                return BigInteger.valueOf(root.get("totalFeatures").asLong());
            } catch (IOException e) {
                log.error("Failed to parse unfiltered count response for {}/{}", uuid, layerName, e);
            }
        }
        return null;
    }

    /**
     * Average bytes per record for the layer in the requested output format.
     * Issues an unfiltered sample download so the result can be reused across calls
     * Cached per (uuid, layerName, outputFormat).
     */
    @Cacheable(CacheConfig.DOWNLOADABLE_SIZE)
    public BigInteger getBytesPerRecord(String uuid, String layerName, String outputFormat) {
        BigInteger totalCount = self.getUnfilteredRecordCount(uuid, layerName);
        if (totalCount == null || totalCount.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        long sampleSize = totalCount.longValue() < SAMPLES_SIZE ? totalCount.longValue() : SAMPLES_SIZE;

        String sampleUrl = prepareWfsRequestUrl(
                uuid, null, null, null, null, layerName, outputFormat, sampleSize, false
        );

        ResponseEntity<byte[]> bytes;
        try {
            bytes = restTemplate.exchange(sampleUrl, HttpMethod.GET, pretendUserEntity, byte[].class);
        } catch (RestClientException e) {
            log.error("WFS sample download failed for {}/{} against server url {}", uuid, layerName, sampleUrl, e);
            throw e;
        }
        if (bytes.getStatusCode().is2xxSuccessful() && bytes.getBody() != null) {
            return BigInteger.valueOf(bytes.getBody().length).divide(BigInteger.valueOf(sampleSize));
        }
        return null;
    }

    /**
     * Estimate download size for the user's subset. Runs the inherently subset-dependent
     * count query, then multiplies by the cached bytes-per-record sample.
     *
     * @return The estimated file size
     */
    public BigInteger estimateDownloadSize(
            String uuid,
            String layerName,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> fields,
            String outputFormat) throws IllegalArgumentException {

        // Subset-filtered count — not cacheable here because the subset would explode the key space.
        String countUrl = prepareWfsRequestUrl(
                uuid, startDate, endDate, multiPolygon, fields, layerName, "application/json", 1L, false
        );

        ResponseEntity<String> countResponse;
        try {
            countResponse = restTemplate.exchange(countUrl, HttpMethod.GET, pretendUserEntity, String.class);
        } catch (RestClientException e) {
            log.error("WFS estimate count request failed for {}/{} against server url {}", uuid, layerName, countUrl, e);
            throw e;
        }

        if (countResponse.getStatusCode().is2xxSuccessful() && countResponse.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(countResponse.getBody());
                if (!root.has("totalFeatures")) {
                    throw new RuntimeException("GeoServer GeoJSON response missing totalFeatures field");
                }
                BigInteger featureCount = BigInteger.valueOf(root.get("totalFeatures").asLong());
                log.debug("Subset record hits {}", featureCount);

                if (featureCount.equals(BigInteger.ZERO)) {
                    return BigInteger.ZERO;
                }

                BigInteger bytesPerRecord = self.getBytesPerRecord(uuid, layerName, outputFormat);
                if (bytesPerRecord == null) {
                    return null;
                }
                return featureCount.multiply(bytesPerRecord);
            } catch (IOException e) {
                log.error("Fail to get feature count for estimate", e);
            }
        }
        return null;
    }

    /**
     * Call the WFS server and stream the downloaded data to the client over SSE
     */
    public void streamWfsDataWithSse(
            String wfsRequestUrl,
            String uuid,
            String layerName,
            String outputFormat,
            SseEmitter emitter,
            AtomicBoolean wfsServerResponded) {
        try {
            doStreamWfsDataWithSse(wfsRequestUrl, uuid, layerName, outputFormat, emitter, wfsServerResponded);
        } catch (HttpStatusCodeException e) {
            log.error("WFS download failed for UUID {} layer {}: server url {} returned status {}",
                    uuid, layerName, wfsRequestUrl, e.getStatusCode(), e);
            throw e;
        } catch (ResourceAccessException e) {
            // A ResourceAccessException raised before the WFS server responded means the
            // server itself is unreachable; after it responded, the wrapped IOException
            // almost always comes from emitter.send() to a disconnected client.
            if (!wfsServerResponded.get()) {
                log.error("WFS download failed for UUID {} layer {}: cannot reach server url {}",
                        uuid, layerName, wfsRequestUrl, e);
            }
            throw e;
        }
    }

    private void doStreamWfsDataWithSse(
            String wfsRequestUrl,
            String uuid,
            String layerName,
            String outputFormat,
            SseEmitter emitter,
            AtomicBoolean wfsServerResponded) {
        restTemplate.execute(
                wfsRequestUrl,
                HttpMethod.GET,
                request -> {
                    // Set headers from pretendUserEntity
                    request.getHeaders().addAll(pretendUserEntity.getHeaders());
                },
                clientHttpResponse -> {
                    // WFS server has responded!
                    wfsServerResponded.set(true);

                    // Send download started confirmation
                    emitter.send(SseEmitter.event()
                            .name(SseEventName.DOWNLOAD_STARTED.getValue())
                            .data(Map.of(
                                    "message", "WFS server responded, starting data stream...",
                                    "timestamp", System.currentTimeMillis()
                            )));

                    InputStream inputStream = clientHttpResponse.getBody();
                    byte[] buffer = new byte[8192]; // 8k buffer
                    int bytesRead;
                    long totalBytes = 0;
                    int chunkNumber = 0;
                    ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        chunkBuffer.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;

                        // Send when buffer >= 16KB **and** size divisible by 3 (for clean Base64)
                        while (chunkBuffer.size() >= chunkSize && chunkBuffer.size() % 3 == 0) {
                            byte[] chunkBytes = chunkBuffer.toByteArray();
                            String encodedData = Base64.getEncoder().encodeToString(chunkBytes);

                            emitter.send(SseEmitter.event()
                                    .name(SseEventName.FILE_CHUNK.getValue())
                                    .data(Map.of(
                                            "chunkNumber", ++chunkNumber,
                                            "data", encodedData,
                                            "chunkSize", chunkBytes.length,
                                            "totalBytes", totalBytes,
                                            "timestamp", System.currentTimeMillis()
                                    ))
                                    .id(String.valueOf(chunkNumber)));

                            chunkBuffer.reset();
                        }
                    }

                    // Final chunk (may not be %3==0, but client Base64 decoder usually handles it)
                    if (chunkBuffer.size() > 0) {
                        String encodedData = Base64.getEncoder().encodeToString(chunkBuffer.toByteArray());
                        emitter.send(SseEmitter.event()
                                .name(SseEventName.FILE_CHUNK.getValue())
                                .data(Map.of(
                                        "chunkNumber", ++chunkNumber,
                                        "data", encodedData,
                                        "chunkSize", chunkBuffer.size(),
                                        "totalBytes", totalBytes,
                                        "final", true
                                )));
                    }

                    // Send completion event
                    emitter.send(SseEmitter.event()
                            .name(SseEventName.DOWNLOAD_COMPLETE.getValue())
                            .data(Map.of(
                                    "totalBytes", totalBytes,
                                    "totalChunks", chunkNumber,
                                    "message", "WFS data download completed successfully",
                                    "media-type", FeatureRequest.GeoServerOutputFormat.fromString(outputFormat).getMediaType(),
                                    "filename", String.format("%s_%s.%s", layerName, uuid, FeatureRequest.GeoServerOutputFormat.fromString(outputFormat).getFileExtension())
                            )));

                    // Close SSE connection with completion
                    emitter.complete();
                    log.info("WFS SSE streaming completed: {} bytes in {} chunks for UUID: {}",
                            totalBytes, chunkNumber, uuid);

                    return null;
                }
        );
    }
}
