package au.org.aodn.ogcapi.server.core.exception;

public class DownloadJobNotFoundException extends RuntimeException {
    public DownloadJobNotFoundException() {
        super("Download job not found");
    }
}
