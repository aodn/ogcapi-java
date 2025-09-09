package au.org.aodn.ogcapi.server.core.exception.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WfsErrorHandler {
    private static final Set<SseEmitter> handledEmitters = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private enum ErrorType {
        NETWORK_ERROR,
        VALIDATION_ERROR,
        UNAUTHORIZED_SERVER_ERROR,
        DOWNLOADABLE_FIELDS_ERROR,
        UNKNOWN_ERROR
    }

    public static void handleError(Exception e, String uuid, SseEmitter emitter, Runnable cleanupFunction) {
        // Prevent double handling
        if (!handledEmitters.add(emitter)) {
            return;
        }

        try {
            ErrorType errorType = categorizeError(e);

            // Clean up any resources that exist
            if (cleanupFunction != null) {
                try {
                    cleanupFunction.run();
                } catch (Exception cleanupException) {
                    log.warn("Cleanup function failed for UUID: {}", uuid, cleanupException);
                }
            }

            switch (errorType) {
                case NETWORK_ERROR -> {
                    log.info("Client disconnected for UUID: {}", uuid);
                    emitter.completeWithError(e);

                }
                case VALIDATION_ERROR -> {
                    log.info("Invalid parameter error for UUID {}: {}", uuid, e.getMessage());
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message", "Invalid parameter error",
                                    "timestamp", System.currentTimeMillis()
                            )));
                    emitter.completeWithError(e);
                }

                case UNAUTHORIZED_SERVER_ERROR -> {
                    log.error("Unauthorized wfs server for UUID {}", uuid, e);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message", "Unauthorized wfs server",
                                    "timestamp", System.currentTimeMillis()
                            )));
                    emitter.completeWithError(e);
                }

                case DOWNLOADABLE_FIELDS_ERROR -> {
                    log.error("No downloadable fields found for UUID {}", uuid, e);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message", "No downloadable fields found",
                                    "timestamp", System.currentTimeMillis()
                            )));
                    emitter.completeWithError(e);
                }

                case UNKNOWN_ERROR -> {
                    log.warn("Unknown error for UUID {}", uuid, e);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "message", "Unknown error: " + e.getMessage(),
                                    "timestamp", System.currentTimeMillis()
                            )));
                    emitter.completeWithError(e);
                }

            }
        } catch (Exception ex) {
            // If we can't handle the error, just log and complete
            log.warn("Error handling failed for UUID: {}", uuid, ex);
            emitter.complete();
        } finally {
            handledEmitters.remove(emitter);
        }
    }

    private static ErrorType categorizeError(Exception e) {
        if (e instanceof IOException || e instanceof IllegalStateException || e.getMessage() != null && (
                e.getMessage().contains("Broken pipe") ||
                        e.getMessage().contains("Connection reset") ||
                        e.getMessage().contains("disconnected client"))) {
            return ErrorType.NETWORK_ERROR;
        }

        // Input validation errors
        if (e instanceof IllegalArgumentException) {
            return ErrorType.VALIDATION_ERROR;
        }

        // Unauthorized wfs error
        if (e instanceof UnauthorizedServerException) {
            return ErrorType.UNAUTHORIZED_SERVER_ERROR;
        }

        // Downloadable fields not found error
        if (e instanceof DownloadableFieldsNotFoundException) {
            return ErrorType.DOWNLOADABLE_FIELDS_ERROR;
        }

        // Default to Unknow error for unexpected errors
        return ErrorType.UNKNOWN_ERROR;
    }
}
