package au.org.aodn.ogcapi.server.processes;

/**
 * The inputs of one {@code download} execute request, as extracted from the OGC Execute
 * body. Carried as a unit so a request that has to wait for a free slot can be submitted
 * later exactly as it arrived.
 */
public record DownloadRequest(
        String uuid,
        String key,
        String startDate,
        String endDate,
        Object multiPolygon,
        String recipient,
        String collectionTitle,
        String fullMetadataLink,
        String suggestedCitation,
        String outputFormat
) {
}
