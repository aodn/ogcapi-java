package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsField;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsFields;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.DescribeLayerResponse;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
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
public class DownloadWfsDataService {
    private final WmsServer wmsServer;
    private final WfsServer wfsServer;
    private final RestTemplate restTemplate;
    private final HttpEntity<?> pretendUserEntity;
    private final int chunkSize;

    public DownloadWfsDataService(
            WmsServer wmsServer,
            WfsServer wfsServer,
            RestTemplate restTemplate,
            @Qualifier("pretendUserEntity") HttpEntity<?> pretendUserEntity,
            @Value("${app.sse.chunkSize:16384}") int chunkSize
    ) {
        this.wmsServer = wmsServer;
        this.wfsServer = wfsServer;
        this.restTemplate = restTemplate;
        this.pretendUserEntity = pretendUserEntity;
        this.chunkSize = chunkSize;
    }

    /**
     * Build CQL filter for temporal and spatial constraints
     */
    protected String buildCqlFilter(String startDate, String endDate, Object multiPolygon, WfsFields wfsFieldModel) {
        StringBuilder cqlFilter = new StringBuilder();

        if (wfsFieldModel == null || wfsFieldModel.getFields() == null) {
            return cqlFilter.toString();
        }

        List<WfsField> fields = wfsFieldModel.getFields();

        // Possible to have multiple days, better to consider all
        List<WfsField> temporalField = fields.stream()
                .filter(field -> "dateTime".equals(field.getType()) || "date".equals(field.getType()))
                .toList();

        // Add temporal filter only if both dates are specified
        if (!temporalField.isEmpty() && startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            List<String> cqls = new ArrayList<>();
            temporalField.forEach(temp ->
                    cqls.add(String.format("(%s DURING %sT00:00:00Z/%sT23:59:59Z)", temp.getName(), startDate, endDate))
            );
            cqlFilter.append("(").append(String.join(" OR ", cqls)).append(")");
        }

        // Find geometry field
        Optional<WfsField> geometryField = fields.stream()
                .filter(field -> "geometrypropertytype".equalsIgnoreCase(field.getType()))
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

//        DescribeLayerResponse describeLayerResponse = wmsServer.describeLayer(uuid, FeatureRequest.builder().layerName(layerName).build());

        String wfsServerUrl;
        String wfsTypeName;
        WfsFields wfsFieldModel;

        // We trust the layername from request to be valid
//        if (describeLayerResponse != null && describeLayerResponse.getLayerDescription().getWfs() != null) {
//            wfsServerUrl = describeLayerResponse.getLayerDescription().getWfs();
//            wfsTypeName = describeLayerResponse.getLayerDescription().getQuery().getTypeName();
//
//            wfsFieldModel = wfsServer.getDownloadableFields(
//                    uuid,
//                    WfsServer.WfsFeatureRequest.builder()
//                            .layerName(layerName)
//                            .server(wfsServerUrl)
//                            .build()
//            );
//            log.info("WFSFieldModel by describeLayer: {}", wfsFieldModel);
//        } else {
        Optional<String> featureServerUrl = wfsServer.getFeatureServerUrlByTitleOrQueryParam(uuid, layerName);

        if (featureServerUrl.isPresent()) {
            wfsServerUrl = featureServerUrl.get();
            wfsTypeName = layerName;
            wfsFieldModel = wfsServer.getDownloadableFields(
                    uuid,
                    WfsServer.WfsFeatureRequest.builder()
                            .layerName(wfsTypeName)
                            .server(wfsServerUrl)
                            .build()
            );
            log.info("WFSFieldModel by wfs typename: {}", wfsFieldModel);
        } else {
            throw new IllegalArgumentException("No WFS server URL found for the given UUID and layer name");
        }
//        }

        // Validate start and end dates
        String validStartDate = DatetimeUtils.validateAndFormatDate(startDate, true);
        String validEndDate = DatetimeUtils.validateAndFormatDate(endDate, false);

        // Build CQL filter
        String cqlFilter = buildCqlFilter(validStartDate, validEndDate, multiPolygon, wfsFieldModel);

        // Build final WFS request URL
        String wfsRequestUrl = wfsServer.createWfsRequestUrl(
                wfsServerUrl,
                wfsTypeName,
                fields,
                cqlFilter,
                outputFormat);

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
