package au.org.aodn.ogcapi.server.core.exception;

public class DownloadJobStatusException extends RuntimeException {
    public DownloadJobStatusException() {
        super("Unable to retrieve download job status");
    }

    public DownloadJobStatusException(Throwable cause) {
        super("Unable to retrieve download job status", cause);
    }
}
