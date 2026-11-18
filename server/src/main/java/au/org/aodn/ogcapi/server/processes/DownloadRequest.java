package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;

import java.util.List;

/**
 * One download execute request. Most values come from the OGC Execute body. The citation and
 * constraints come from the Elasticsearch record (DownloadCitationService). Carried as a unit
 * so a request that has to wait for a free slot can be submitted later exactly as it arrived.
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
        List<String> usageConstraints,
        String outputFormat,
        // Size the portal already estimated, null when the request did not carry one
        Long estimatedSizeBytes
) {
    public DownloadRequest {
        // Null means no constraints to show
        usageConstraints = usageConstraints == null ? List.of() : usageConstraints;
        // A negative size is not a real estimate, treat it as missing
        estimatedSizeBytes = estimatedSizeBytes != null && estimatedSizeBytes < 0 ? null : estimatedSizeBytes;
    }

    // A request that carries no estimated size
    public DownloadRequest(String uuid, String key, String startDate, String endDate, Object multiPolygon,
                           String recipient, String collectionTitle, String fullMetadataLink,
                           String suggestedCitation, List<String> usageConstraints, String outputFormat) {
        this(uuid, key, startDate, endDate, multiPolygon, recipient, collectionTitle, fullMetadataLink,
                suggestedCitation, usageConstraints, outputFormat, null);
    }

    // True for the original NetCDF files list download
    public boolean isOriginalNetcdfs() {
        return DatasetDownloadEnums.ORIGINAL_NETCDFS_KEY.equals(key);
    }
}
