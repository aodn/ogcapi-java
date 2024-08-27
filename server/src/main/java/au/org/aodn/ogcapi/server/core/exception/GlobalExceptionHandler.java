package au.org.aodn.ogcapi.server.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import au.org.aodn.ogcapi.server.core.model.ErrorResponse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    public GlobalExceptionHandler() {
        log.info("Enable GlobalExceptionHandler");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            CustomException.class
    })
    public ResponseEntity<ErrorResponse> handleCustomException(Exception ex, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse
                .builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .details(request.getDescription(true))
                .parameters(request
                        .getParameterMap()
                        .keySet()
                        .stream()
                        .map(key -> key + "=" + Arrays.toString(request.getParameterMap().get(key)))
                        .collect(Collectors.joining(", ", "{", "}"))).build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse
                .builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .details(request.getDescription(false)).build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
