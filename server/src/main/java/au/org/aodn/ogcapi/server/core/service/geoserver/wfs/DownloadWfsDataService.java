package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.geoserver.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DownloadWfsDataService extends Server {
    private final RestTemplate restTemplate;
    private final HttpEntity<?> pretendUserEntity;
    private final int chunkSize;

    public DownloadWfsDataService(
            WfsServer wfsServer,
            RestTemplate restTemplate,
            @Qualifier("pretendUserEntity") HttpEntity<?> pretendUserEntity,
            @Value("${app.sse.chunkSize:16384}") int chunkSize
    ) {
        super(wfsServer);
        this.restTemplate = restTemplate;
        this.pretendUserEntity = pretendUserEntity;
        this.chunkSize = chunkSize;
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
            String outputFormat) {


        // Get WFS server URL and field model for the given UUID and layer name
        Optional<String> featureServerUrl = wfsServer.getFeatureServerUrl(uuid, layerName);

        // Get the wfs fields to build the CQL filter
        if (featureServerUrl.isPresent()) {
            String wfsServerUrl = featureServerUrl.get();

            // Build CQL filter
            String cqlFilter = buildCqlFilter(wfsServerUrl, uuid, layerName, startDate, endDate, multiPolygon);

            // Build final WFS request URL
            String wfsRequestUrl = wfsServer.createWfsRequestUrl(
                    wfsServerUrl,
                    layerName,
                    fields,
                    cqlFilter,
                    outputFormat);

            log.info("Prepared WFS request URL: {}", wfsRequestUrl);
            return wfsRequestUrl;
        } else {
            throw new IllegalArgumentException("No WFS server URL found for the given UUID and layer name");
        }
    }

    /**
     * Execute WFS request with SSE support
     */
    public void executeWfsRequestWithSse(
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
                            .name("download-started")
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
                                    .name("file-chunk")
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
                                .name("file-chunk")
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
                            .name("download-complete")
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
