package au.org.aodn.ogcapi.server.core.service.sse;

import au.org.aodn.ogcapi.server.core.exception.wfs.WfsErrorHandler;
import au.org.aodn.ogcapi.server.core.model.enumeration.SseEventName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A single SSE stream's runtime state: the underlying {@link SseEmitter}, an
 * optional keep-alive ticker, and the cleanup of those resources.
 * <p>
 * Created and managed by {@link SseStreamHandler}; the work lambda receives one
 * to send events and (optionally) start a keep-alive.
 */
@Slf4j
public class SseSession {

    private final String contextId;

    @Getter
    private final SseEmitter emitter;

    private final AtomicReference<ScheduledFuture<?>> keepAliveTaskRef = new AtomicReference<>();
    private final AtomicReference<ScheduledExecutorService> keepAliveExecutorRef = new AtomicReference<>();

    public SseSession(String contextId, SseEmitter emitter) {
        this.contextId = contextId;
        this.emitter = emitter;
    }

    /**
     * Send a named SSE event with the given payload.
     */
    public void send(SseEventName eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName.getValue()).data(data));
    }

    /**
     * Start sending a {@code keep-alive} event every {@code intervalSeconds}. The
     * payload is recomputed each tick by {@code payloadSupplier} so callers can
     * reflect changing state (e.g. whether an upstream server has responded yet).
     */
    public void startKeepAlive(long intervalSeconds, Supplier<Object> payloadSupplier) {
        ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                send(SseEventName.KEEP_ALIVE, payloadSupplier.get());
            } catch (Exception e) {
                WfsErrorHandler.handleError(e, contextId, emitter, this::cleanup);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        keepAliveTaskRef.set(keepAliveTask);
        keepAliveExecutorRef.set(keepAliveExecutor);
    }

    /**
     * Complete the stream, closing the connection to the client.
     */
    public void complete() {
        emitter.complete();
    }

    /**
     * Cancel the keep-alive task and shut down its executor. Idempotent.
     */
    public void cleanup() {
        try {
            ScheduledFuture<?> keepAliveTask = keepAliveTaskRef.get();
            if (keepAliveTask != null && !keepAliveTask.isCancelled()) {
                keepAliveTask.cancel(false);
            }

            ScheduledExecutorService keepAliveExecutor = keepAliveExecutorRef.get();
            if (keepAliveExecutor != null && !keepAliveExecutor.isShutdown()) {
                keepAliveExecutor.shutdown();
            }
        } catch (Exception e) {
            log.error("Error during cleanup for SSE stream: {}", contextId, e);
        }
    }
}
