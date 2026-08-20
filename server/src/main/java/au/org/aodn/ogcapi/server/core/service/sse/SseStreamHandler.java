package au.org.aodn.ogcapi.server.core.service.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shared scaffolding for the long-running SSE endpoints (WFS download / estimate,
 * cloud-optimised estimate). It owns the boilerplate that every stream needs —
 * emitter creation, lifecycle callbacks, resource cleanup, and error handling —
 * so callers only supply the actual work.
 */
@Slf4j
public class SseStreamHandler {

    private static final int CORE_STREAMS = 4;
    private static final int MAX_STREAMS = 64;
    private static final long IDLE_THREAD_KEEP_ALIVE_SECONDS = 60L;

    /**
     * Streams block on upstream sockets for minutes, so they get their own pool rather than
     * ForkJoinPool.commonPool(), which runs a single thread on a 2-vCPU container and cannot
     * see a blocking socket read, so one slow estimate made every other SSE request wait.
     * The SynchronousQueue means a stream that cannot get a thread is rejected straight away
     * rather than queueing behind work that may run for minutes.
     */
    private static final ExecutorService STREAM_EXECUTOR = new ThreadPoolExecutor(
            CORE_STREAMS,
            MAX_STREAMS,
            IDLE_THREAD_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new CustomizableThreadFactory("sse-stream-"));

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
     * SseErrorHandler. The work is responsible for completing the stream
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
                SseErrorHandler.handleError((Exception) throwable, contextId, emitter, session::cleanup));

        try {
            STREAM_EXECUTOR.execute(() -> {
                try {
                    work.run(session);
                } catch (Exception e) {
                    SseErrorHandler.handleError(e, contextId, emitter, session::cleanup);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("No SSE worker available for {}; {} streams already running", contextId, MAX_STREAMS);
            SseErrorHandler.handleError(e, contextId, emitter, session::cleanup);
        }

        return emitter;
    }
}
