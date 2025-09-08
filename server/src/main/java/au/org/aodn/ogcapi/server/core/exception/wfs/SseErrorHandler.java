package au.org.aodn.ogcapi.server.core.exception.wfs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SseErrorHandler {
    private static final Set<SseEmitter> handledEmitters = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public enum ErrorSeverity {
        CLIENT_DISCONNECTED,
        LOW,
        MEDIUM,
        HIGH
    }

    public static void handleError(Exception e, String uuid, SseEmitter emitter) {
        handleError(e, uuid, emitter, null, null, null);
    }

    public static void handleError(Exception e, String uuid, SseEmitter emitter,
                                   AtomicBoolean downloadCompleted, ScheduledFuture<?> keepAliveTask,
                                   ScheduledExecutorService keepAliveExecutor) {
        // Prevent double handling
        if (!handledEmitters.add(emitter)) {
            return;
        }

        try {
            ErrorSeverity severity = categorizeError(e);

            // Clean up any resources that exist
            if (downloadCompleted != null) {
                downloadCompleted.set(true);
            }
            if (keepAliveTask != null) {
                keepAliveTask.cancel(false);
            }
            if (keepAliveExecutor != null) {
                keepAliveExecutor.shutdown();
            }

            switch (severity) {
                case CLIENT_DISCONNECTED -> {
                    log.info("Client disconnected during streaming for UUID: {}", uuid);
                    emitter.complete();
                }
                case LOW -> {
                    log.info("Non-critical error for UUID {}: {}", uuid, e.getMessage());
                    emitter.complete();
                }
                case MEDIUM -> {
                    log.warn("Important error for UUID {}", uuid, e);
                    emitter.completeWithError(e);
                }
                case HIGH -> {
                    log.error("Critical error for UUID {}", uuid, e);
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

    private static ErrorSeverity categorizeError(Exception e) {
        if (e.getMessage() != null && (
                e.getMessage().contains("Broken pipe") ||
                        e.getMessage().contains("Connection reset") ||
                        e.getMessage().contains("disconnected client"))) {
            return ErrorSeverity.CLIENT_DISCONNECTED;
        }

        // Categorize other errors
        if (e instanceof IllegalArgumentException) {
            // Input validation errors
            return ErrorSeverity.MEDIUM;
        } else if (e instanceof RuntimeException && e.getMessage() != null &&
                (e.getMessage().contains("WFS URL not authorized") ||
                        e.getMessage().contains("Collection not found"))) {
            // Important business logic errors
            return ErrorSeverity.HIGH;
        }

        // Default to LOW for unexpected errors
        return ErrorSeverity.LOW;
    }
}
