package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.wfs.WfsInfo;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DownloadWfsDataService {
    private final Search elasticSearch;
    private final DownloadableFieldsService downloadableFieldsService;
    private final WfsServer wfsServer;
    private final RestTemplate restTemplate;

    public DownloadWfsDataService(
            Search elasticSearch,
            DownloadableFieldsService downloadableFieldsService,
            WfsServer wfsServer,
            RestTemplate restTemplate) {
        this.elasticSearch = elasticSearch;
        this.downloadableFieldsService = downloadableFieldsService;
        this.wfsServer = wfsServer;
        this.restTemplate = restTemplate;
    }

    /**
     * Build CQL filter for temporal and spatial constraints
     */
    private String buildCqlFilter(String startDate, String endDate, Object multiPolygon, List<DownloadableFieldModel> downloadableFields) {
        StringBuilder cqlFilter = new StringBuilder();

        // Find temporal field
        Optional<DownloadableFieldModel> temporalField = downloadableFields.stream()
                .filter(field -> "dateTime".equals(field.getType()) || "date".equals(field.getType()))
                .findFirst();

        // Add temporal filter
        if (temporalField.isPresent() && startDate != null && endDate != null) {
            String fieldName = temporalField.get().getName();
            cqlFilter.append(fieldName)
                    .append(" DURING ")
                    .append(startDate).append("T00:00:00Z/")
                    .append(endDate).append("T23:59:59Z");
        }

        // Find geometry field
        Optional<DownloadableFieldModel> geometryField = downloadableFields.stream()
                .filter(field -> "geometrypropertytype".equals(field.getType()))
                .findFirst();

        // Add spatial filter
        if (geometryField.isPresent() && multiPolygon != null) {
            String fieldName = geometryField.get().getName();

            String wkt = GeometryUtils.convertToWkt(multiPolygon);

            if ((wkt != null) && !cqlFilter.isEmpty()) {
                cqlFilter.append(" AND ");
            }

            if (wkt != null) {
                cqlFilter.append("INTERSECTS(")
                        .append(fieldName)
                        .append(",")
                        .append(wkt)
                        .append(")");
            }
        }

        return cqlFilter.toString();
    }

    /**
     * Build WFS GetFeature URL
     */
    private String buildWfsUrl(String wfsUrl, String layerName, String cqlFilter) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(wfsUrl)
                .queryParam("service", "WFS")
                .queryParam("version", "1.0.0")
                .queryParam("request", "GetFeature")
                .queryParam("typeName", layerName)
                .queryParam("outputFormat", "text/csv");

        if (cqlFilter != null && !cqlFilter.isEmpty()) {
            builder.queryParam("cql_filter", cqlFilter);
        }

        return builder.build().toUriString();
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
            String layerName) {

        // Extract WFS URL and layer name from collection links
        WfsInfo wfsInfo = wfsServer.getWfsInfo(uuid, layerName);
        if (wfsInfo == null) {
            throw new RuntimeException("No WFS link found for collection " + uuid + " with layer name " + layerName);
        }

        // Validate and get approved WFS URL from whitelist
        String approvedWfsUrl;
        approvedWfsUrl = wfsServer.validateAndGetApprovedServerUrl(wfsInfo.wfsUrl());
        log.info("Using approved WFS URL: {} (original: {})", approvedWfsUrl, wfsInfo.wfsUrl());

        // Get downloadable fields to map field names
        List<DownloadableFieldModel> downloadableFields =
                downloadableFieldsService.getFilterFieldsFromWfs(approvedWfsUrl, wfsInfo.layerName());
        log.info("DownloadableFields: {}", downloadableFields);

        // Validate start and end dates
        String validStartDate = DatetimeUtils.validateAndFormatDate(startDate, true);
        String validEndDate = DatetimeUtils.validateAndFormatDate(endDate, false);

        // Build CQL filter
        String cqlFilter = buildCqlFilter(validStartDate, validEndDate, multiPolygon, downloadableFields);

        // Build final WFS URL
        String wfsRequestUrl = buildWfsUrl(approvedWfsUrl, wfsInfo.layerName(), cqlFilter);

        log.info("Prepared WFS request URL: {}", wfsRequestUrl);
        return wfsRequestUrl;
    }

    /**
     * Execute WFS request with SSE support
     */
    public void executeWfsRequestWithSse(
            String wfsRequestUrl,
            String uuid,
            String layerName,
            SseEmitter emitter,
            AtomicBoolean wfsServerResponded) {
        restTemplate.execute(
                wfsRequestUrl,
                HttpMethod.GET,
                null,
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
                    long lastProgressTime = System.currentTimeMillis();
                    ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        chunkBuffer.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;

                        long currentTime = System.currentTimeMillis();

                        // Send chunk when buffer is full OR every 2 seconds
                        if (chunkBuffer.size() >= 16384 ||
                                (currentTime - lastProgressTime >= 2000)) {

                            byte[] chunkBytes = chunkBuffer.toByteArray();

                            // Ensure Base64 alignment
                            // Base64 works in 3-byte groups, so chunk size should be divisible by 3
                            int alignedSize = (chunkBytes.length / 3) * 3;

                            if (alignedSize > 0) {
                                // Send the aligned portion
                                byte[] alignedChunk = Arrays.copyOf(chunkBytes, alignedSize);
                                String encodedData = Base64.getEncoder().encodeToString(alignedChunk);

                                emitter.send(SseEmitter.event()
                                        .name("file-chunk")
                                        .data(Map.of(
                                                "chunkNumber", ++chunkNumber,
                                                "data", encodedData,
                                                "chunkSize", alignedChunk.length,
                                                "totalBytes", totalBytes,
                                                "timestamp", currentTime
                                        ))
                                        .id(String.valueOf(chunkNumber)));

                                // Keep the remaining bytes for next chunk
                                if (alignedSize < chunkBytes.length) {
                                    byte[] remainder = Arrays.copyOfRange(chunkBytes, alignedSize, chunkBytes.length);
                                    chunkBuffer.reset();
                                    chunkBuffer.write(remainder);
                                } else {
                                    chunkBuffer.reset();
                                }

                                lastProgressTime = currentTime;
                            }
                        }
                    }

                    // Send final chunk if any remains
                    if (chunkBuffer.size() > 0) {
                        String encodedData = Base64.getEncoder()
                                .encodeToString(chunkBuffer.toByteArray());
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
                                    "filename", layerName + "_" + uuid + ".csv"
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
