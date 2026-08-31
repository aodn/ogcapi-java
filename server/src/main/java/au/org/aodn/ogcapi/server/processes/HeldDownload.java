package au.org.aodn.ogcapi.server.processes;

import java.time.Instant;
import java.util.Map;

/**
 * A download that was accepted but not yet submitted to AWS Batch, because its owner was at
 * the per-user concurrency limit.
 *
 * The Batch job name and parameters are built at accept time rather than at release time so
 * that releasing is a plain submit, and so the status endpoint can describe a held job -
 * collection, format, metadata link - without any AWS call.
 */
record HeldDownload(
        String jobId,
        DownloadRequest request,
        String jobName,
        Map<String, String> parameters,
        Instant acceptedAt,
        int attempts
) {
    HeldDownload withAttempt() {
        return new HeldDownload(jobId, request, jobName, parameters, acceptedAt, attempts + 1);
    }
}
