package au.org.aodn.ogcapi.server.core.service.sse;

import au.org.aodn.ogcapi.server.core.exception.wfs.WfsErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * Shared scaffolding for the long-running SSE endpoints (WFS download / estimate,
 * cloud-optimised estimate). It owns the boilerplate that every stream needs —
 * emitter creation, lifecycle callbacks, resource cleanup, and error handling —
 * so callers only supply the actual work.
 */
@Slf4j
public class SseStreamHandler {

    private SseStreamHandler() {
    }

    /**
     * Work executed against an {@link SseSession}. Allowed to throw so callers can
     * let {@code emitter.send(...)} (which throws {@link java.io.IOException})
     * propagate to the shared error handler.
     */
    @FunctionalInterface
    public interface SseWork {
        void run(SseSession session) throws Exception;
    }

    /**
     * Create an SSE stream and run {@code work} asynchronously against it.
     * <p>
     * A never-timing-out {@link SseEmitter} is created, lifecycle callbacks are
     * wired to clean up the keep-alive resources, and any exception from the work
     * (including validation errors thrown at the start) is routed through
     * {@link WfsErrorHandler}. The work is responsible for completing the stream
     * once its result has been sent.
     *
     * @param contextId identifier (e.g. uuid) used for logging and error handling
     * @param work      the per-stream logic: send events, optionally start keep-alive
     * @return the emitter to return from the controller
     */
    public static SseEmitter stream(String contextId, SseWork work) {
        final SseEmitter emitter = new SseEmitter(0L);
        final SseSession session = new SseSession(contextId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE stream completion for {}", contextId);
            session.cleanup();
        });

        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for {}", contextId);
            session.cleanup();
        });

        emitter.onError(throwable ->
                WfsErrorHandler.handleError((Exception) throwable, contextId, emitter, session::cleanup));

        CompletableFuture.runAsync(() -> {
            try {
                work.run(session);
            } catch (Exception e) {
                WfsErrorHandler.handleError(e, contextId, emitter, session::cleanup);
            }
        });

        return emitter;
    }
}
