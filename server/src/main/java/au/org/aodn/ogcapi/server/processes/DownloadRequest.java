package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;

import java.util.List;

/**
 * The inputs of one {@code download} execute request, as extracted from the OGC Execute
 * body. Carried as a unit so a request that has to wait for a free slot can be submitted
 * later exactly as it arrived.
 * <p>
 * Empty when the dataset has none, or when the input is missing.
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
        String outputFormat
) {
    public DownloadRequest {
        // A missing input means no constraints to show
        usageConstraints = usageConstraints == null ? List.of() : usageConstraints;
    }

    // True for the original NetCDF files list download
    public boolean isOriginalNetcdfs() {
        return DatasetDownloadEnums.ORIGINAL_NETCDFS_KEY.equals(key);
    }
}
