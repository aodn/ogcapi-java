package au.org.aodn.ogcapi.server.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * Carries an upstream-DAS response status/body/content-type up to {@link GlobalExceptionHandler}
 * so DasTilerService can decide the client-facing status once, in one place, per the status
 * mapping described in the visual-tile proxy plan (400/404/422/429/503 mirrored, 401/403/other
 * 5xx folded to 502, timeouts to 504).
 */
public class DasUpstreamException extends RuntimeException {

    private final HttpStatus status;
    private final byte[] body;
    private final MediaType contentType;

    public DasUpstreamException(HttpStatus status, byte[] body, MediaType contentType) {
        super("DAS upstream error: " + status);
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    public static DasUpstreamException withDetail(HttpStatus status, String detail) {
        String json = "{\"detail\":\"" + detail.replace("\"", "'") + "\"}";
        return new DasUpstreamException(status, json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public byte[] getBody() {
        return body;
    }

    public MediaType getContentType() {
        return contentType;
    }
}
