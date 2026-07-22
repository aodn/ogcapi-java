package au.org.aodn.ogcapi.server.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DasUpstreamException extends RuntimeException {

    private final HttpStatus status;

    public DasUpstreamException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
