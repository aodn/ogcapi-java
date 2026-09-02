package au.org.aodn.ogcapi.server.core.exception;

public class DownloadLimitExceededException extends RuntimeException {
    public DownloadLimitExceededException(int maxConcurrent) {
        super("You already have " + maxConcurrent + " downloads in progress. "
                + "Wait for one of them to complete before starting another.");
    }
}
