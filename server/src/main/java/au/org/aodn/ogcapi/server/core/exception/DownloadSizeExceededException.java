package au.org.aodn.ogcapi.server.core.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DownloadSizeExceededException extends RuntimeException {

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024 * 1024);

    public DownloadSizeExceededException(long estimatedBytes, long maxBytes) {
        super("The selected data is too large to download (estimated " + toGb(estimatedBytes)
                + " GB, limit " + toGb(maxBytes) + " GB). "
                + "Please reduce the date range or area and try again.");
    }

    private static String toGb(long bytes) {
        return BigDecimal.valueOf(bytes)
                .divide(BYTES_PER_GB, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
