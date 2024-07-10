package au.org.aodn.ogcapi.server.core.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Please refer to GlobalExceptionHandler, you do not need to create it manually, and should be handled by the
 * handler automatically.
 */
@Data
@NoArgsConstructor
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private String message;
    private String details;
    private String parameters;

    public ErrorResponse(LocalDateTime timestamp, String message, String details, String parameters) {
        this.timestamp = timestamp;
        this.message = message;
        this.details = details;
        this.parameters = parameters;
    }
}
