package au.org.aodn.ogcapi.server.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DownloadableFieldsNotFoundException extends RuntimeException {
    public DownloadableFieldsNotFoundException(String message) {
        super(message);
    }
}
