package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.configuration.WfsServerConfig;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DownloadWfsDataService {

    @Autowired
    private ElasticSearch elasticSearch;

    @Autowired
    private DownloadableFieldsService downloadableFieldsService;

    @Autowired
    private WfsServerConfig wfsServerConfig;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Download WFS data and stream directly to client
     */
    public ResponseEntity<StreamingResponseBody> downloadWfsData(
            String uuid,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> fields,
            String layerName) {

        try {
            // Get collection information from UUID
            ElasticSearch.SearchResult<StacCollectionModel> searchResult =
                    elasticSearch.searchCollections(List.of(uuid), null);

            if (searchResult.getCollections().isEmpty()) {
                log.error("Collection with UUID {} not found", uuid);
                return ResponseEntity.notFound().build();
            }

            StacCollectionModel collection = searchResult.getCollections().get(0);

            // Extract WFS URL and layer name from collection links
            WfsInfo wfsInfo = extractWfsInfo(collection, layerName);
            if (wfsInfo == null) {
                log.error("No WFS link found for collection {} with layer name {}", uuid, layerName);
                return ResponseEntity.badRequest().build();
            }

            // Validate and get approved WFS URL from whitelist (same as DownloadableFieldsService)
            String approvedWfsUrl;
            try {
                approvedWfsUrl = wfsServerConfig.validateAndGetApprovedServerUrl(wfsInfo.wfsUrl);
                log.info("Using approved WFS URL: {} (original: {})", approvedWfsUrl, wfsInfo.wfsUrl);
            } catch (Exception e) {
                log.error("WFS URL not authorized: {}", wfsInfo.wfsUrl, e);
                return ResponseEntity.badRequest().build();
            }

            // Get downloadable fields to map field names
            List<DownloadableFieldModel> downloadableFields =
                    downloadableFieldsService.getDownloadableFields(approvedWfsUrl, wfsInfo.layerName);

            // Build CQL filter
            String cqlFilter = buildCqlFilter(startDate, endDate, multiPolygon, downloadableFields);

            // Build WFS URL using approved URL
            String wfsRequestUrl = buildWfsUrl(approvedWfsUrl, wfsInfo.layerName, cqlFilter);

            log.info("Downloading WFS data from: {}", wfsRequestUrl);

            // Create streaming response body
            StreamingResponseBody streamingResponseBody = outputStream -> {
                try {
                    restTemplate.execute(
                            wfsRequestUrl,
                            HttpMethod.GET,
                            null,
                            clientHttpResponse -> {
                                try (InputStream inputStream = clientHttpResponse.getBody()) {
                                    // Stream data directly from WFS server to client
                                    byte[] buffer = new byte[8192]; // 8KB buffer
                                    int bytesRead;
                                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                                        outputStream.write(buffer, 0, bytesRead);
                                        outputStream.flush();
                                    }
                                    log.info("Successfully streamed WFS data for UUID: {}", uuid);
                                    return null;
                                } catch (IOException e) {
                                    log.error("Error streaming WFS data for UUID: {}", uuid, e);
                                    throw new RuntimeException("Error streaming WFS data", e);
                                }
                            }
                    );
                } catch (Exception e) {
                    log.error("Error executing WFS request for UUID: {}", uuid, e);
                    throw new RuntimeException("Error executing WFS request", e);
                }
            };

            // Return streaming response with proper headers
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + layerName + "_" + uuid + ".csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(streamingResponseBody);

        } catch (Exception e) {
            log.error("Error downloading WFS data for UUID {}", uuid, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Extract WFS URL and layer name from collection links
     */
    private WfsInfo extractWfsInfo(StacCollectionModel collection, String layerName) {
        if (collection.getLinks() == null) {
            return null;
        }

        // Find WFS link with matching layer name (title)
        Optional<LinkModel> wfsLink = collection.getLinks().stream()
                .filter(link -> "wfs".equals(link.getRel()))
                .filter(link -> layerName.equals(link.getTitle()))
                .findFirst();

        if (wfsLink.isEmpty()) {
            log.warn("No WFS link found with layer name: {}", layerName);
            return null;
        }

        String href = wfsLink.get().getHref();
        String title = wfsLink.get().getTitle();

        if (href == null || title == null) {
            return null;
        }

        // The href is the WFS server URL, title is the layer name
        return new WfsInfo(href, title);
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
            if (!cqlFilter.isEmpty()) {
                cqlFilter.append(" AND ");
            }

            String fieldName = geometryField.get().getName();
            // Convert multiPolygon to WKT format
            String wktGeometry = convertToWkt(multiPolygon);
            if (wktGeometry != null) {
                cqlFilter.append("INTERSECTS(")
                        .append(fieldName)
                        .append(",")
                        .append(wktGeometry)
                        .append(")");
            }
        }

        return cqlFilter.toString();
    }

    /**
     * Convert multiPolygon object to WKT format
     */
    private String convertToWkt(Object multiPolygon) {
        // Simplified conversion - assuming multiPolygon is a simple bbox for now
        // In a real implementation, you'd parse the actual polygon structure
        // For example: POLYGON((110 -45,160 -45,160 -5,110 -5,110 -45))

        // This is a placeholder - implement proper polygon conversion based on your data structure
        if (multiPolygon instanceof String) {
            return (String) multiPolygon;
        }

        return null;
    }

    /**
     * Build WFS GetFeature URL
     */
    private String buildWfsUrl(String wfsUrl, String layerName, String cqlFilter) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(wfsUrl)
                .queryParam("service", "WFS")
                .queryParam("version", "2.0.0")
                .queryParam("request", "GetFeature")
                .queryParam("typeName", layerName)
                .queryParam("outputFormat", "text/csv");

        if (cqlFilter != null && !cqlFilter.isEmpty()) {
            builder.queryParam("cql_filter", cqlFilter);
        }

        return builder.build().toUriString();
    }

    /**
     * Inner class to hold WFS information
     */
    private static class WfsInfo {
        final String wfsUrl;
        final String layerName;

        WfsInfo(String wfsUrl, String layerName) {
            this.wfsUrl = wfsUrl;
            this.layerName = layerName;
        }
    }
}
