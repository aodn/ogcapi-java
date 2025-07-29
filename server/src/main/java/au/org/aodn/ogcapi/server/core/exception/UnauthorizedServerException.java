package au.org.aodn.ogcapi.server.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedServerException extends RuntimeException {
    public UnauthorizedServerException(String message) {
        super(message);
    }
}
