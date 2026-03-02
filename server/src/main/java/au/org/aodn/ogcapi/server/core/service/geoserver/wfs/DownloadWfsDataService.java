package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.configuration.CacheConfig;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import lombok.extern.slf4j.Slf4j;
import net.opengis.ows10.ExceptionReportType;
import net.opengis.wfs.FeatureCollectionType;
import org.geotools.wfs.v1_1.WFSConfiguration;
import org.geotools.xsd.Parser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DownloadWfsDataService {
    protected final WfsServer wfsServer;
    protected final RestTemplate restTemplate;
    protected final HttpEntity<?> pretendUserEntity;
    protected final int chunkSize;
    protected static final WFSConfiguration WFS_CONFIG = new WFSConfiguration();
    protected static final int SAMPLES_SIZE = 500;    // A not too small sample for download size estimation

    public DownloadWfsDataService(
            WfsServer wfsServer,
            RestTemplate restTemplate,
            HttpEntity<?> pretendUserEntity,
            int chunkSize
    ) {
        this.wfsServer = wfsServer;
        this.pretendUserEntity = pretendUserEntity;
        this.chunkSize = chunkSize;
        this.restTemplate = restTemplate;
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
     * We just need to estimate the download size, the way we do it is issue two query:
     * a. Issue a query and get the number or record hit
     * b. Issue a query with data download but then limit the records size, and do a liner interpolation
     * @return The estimated file size
     */
    @Cacheable(CacheConfig.DOWNLOADABLE_SIZE)
    public BigInteger estimateDownloadSize(
            String uuid,
            String layerName,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> fields,
            String outputFormat) throws IllegalArgumentException {

        // Just get number of record, the reply will always in XML
        String wfsRequestUrl = prepareWfsRequestUrl(
                uuid, startDate, endDate, multiPolygon, fields, layerName, "",  -1L, true
        );

        ResponseEntity<String> response = restTemplate.exchange(wfsRequestUrl, HttpMethod.GET, pretendUserEntity, String.class);

        if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Parser parser = new Parser(WFS_CONFIG);
            parser.setValidating(false);
            parser.setFailOnValidationError(false);

            try {
                Object o = parser.parse(new StringReader(response.getBody()));
                if(o instanceof FeatureCollectionType hits) {
                    BigInteger featureCount = hits.getNumberOfFeatures();

                    log.debug("Total record hits {}", featureCount);
                    // Now we need to do another query where we limited the record count to something small
                    wfsRequestUrl = prepareWfsRequestUrl(
                            uuid, startDate, endDate, multiPolygon, fields, layerName, outputFormat, SAMPLES_SIZE, false
                    );
                    ResponseEntity<byte[]> bytes = restTemplate.exchange(wfsRequestUrl, HttpMethod.GET, pretendUserEntity, byte[].class);
                    if(bytes.getStatusCode().is2xxSuccessful() && bytes.getBody() != null) {
                        return featureCount
                                .multiply(BigInteger.valueOf(bytes.getBody().length))
                                .divide(BigInteger.valueOf(SAMPLES_SIZE));
                    }
                }
                else if(o instanceof ExceptionReportType report) {
                    throw new IllegalArgumentException(String.join(",", report.getException().stream().map(ex -> ex.getExceptionText().toString()).toList()));
                }
            }
            catch(IOException | SAXException | ParserConfigurationException e) {
                log.error("Fail to convert wfs hits result", e);
            }
        }
        return null;
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
