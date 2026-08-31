package au.org.aodn.ogcapi.server.processes;

/**
 * The outcome of accepting a download: the job id the caller polls, and whether it went
 * straight to AWS Batch or is waiting for one of that user's slots to free.
 *
 * @param jobId         the AWS Batch job id when submitted, or a locally minted id when held
 * @param queued        true while the download is waiting rather than running
 * @param queuePosition how many of this user's downloads, including this one, are waiting
 *                      ahead of it. 1 means it is next. Null when not queued.
 */
public record DownloadAdmission(String jobId, boolean queued, Integer queuePosition) {

    static DownloadAdmission submitted(String awsJobId) {
        return new DownloadAdmission(awsJobId, false, null);
    }

    static DownloadAdmission queued(String jobId, int queuePosition) {
        return new DownloadAdmission(jobId, true, queuePosition);
    }
}
