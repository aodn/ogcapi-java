package au.org.aodn.ogcapi.server.core.model.enumeration;

import lombok.Getter;

/**
 * Names of the SSE events sent by the download / size-estimate streams.
 * <p>
 * The frontend identifies events by these literal names, so they form a wire
 * contract — change a value only together with the portal (and keep them
 * aligned with the data-access-service sse_wrapper vocabulary).
 */
@Getter
public enum SseEventName {
    CONNECTION_ESTABLISHED("connection-established"),
    KEEP_ALIVE("keep-alive"),
    DOWNLOAD_STARTED("download-started"),
    FILE_CHUNK("file-chunk"),
    DOWNLOAD_COMPLETE("download-complete"),
    ESTIMATE_COMPLETE("estimate-complete"),
    ESTIMATE_FAILED("estimate-failed"),
    ERROR("error");

    private final String value;

    SseEventName(String value) {
        this.value = value;
    }
}
