package au.org.aodn.ogcapi.server.core.service.sse;

import au.org.aodn.ogcapi.server.core.exception.SseClientGoneException;
import au.org.aodn.ogcapi.server.core.model.enumeration.SseEventName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    // What a probe writes. Only ever read by whatever is proxying this stream, which cares that
    // bytes moved and not what they say, so it is short.
    private static final String PROBE_COMMENT = "probe";

    private final String contextId;

    @Getter
    private final SseEmitter emitter;

    private final AtomicReference<ScheduledFuture<?>> keepAliveTaskRef = new AtomicReference<>();
    private final AtomicReference<ScheduledExecutorService> keepAliveExecutorRef = new AtomicReference<>();

    // When an event last reached the client, so the keep-alive can tell a quiet stream from a
    // busy one. Probes are writes but not events and deliberately do not count, see probeClient.
    // Starts at creation: nothing has been sent yet, but nothing is overdue either.
    private final AtomicLong lastEventSentAt = new AtomicLong(System.currentTimeMillis());

    public SseSession(String contextId, SseEmitter emitter) {
        this.contextId = contextId;
        this.emitter = emitter;
    }

    /**
     * Send a named SSE event with the given payload.
     */
    public void send(SseEventName eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName.getValue()).data(data));
        lastEventSentAt.set(System.currentTimeMillis());
    }

    /**
     * Write to the client and report a dead one as SseClientGoneException.
     * Call it from the thread blocked upstream, so the exception unwinds that read and closes
     * the connection instead of leaving a server computing a result for nobody.
     * It writes an SSE comment, not an event: still a real write, but the browser ignores it and
     * the keep-alive ticker does not count it as activity.
     */
    public void probeClient() throws SseClientGoneException {
        try {
            emitter.send(SseEmitter.event().comment(PROBE_COMMENT));
        } catch (IOException e) {
            throw new SseClientGoneException(contextId, e);
        }
    }

    /**
     * Keep the client's connection busy with a keep-alive event every intervalSeconds, skipping
     * a tick when an event was sent within the last half interval so work that reports its own
     * progress is not doubled up on. payloadSupplier is called each tick, so the payload can
     * reflect current state.
     */
    public void startKeepAlive(long intervalSeconds, Supplier<Object> payloadSupplier) {
        long quietEnoughMillis = intervalSeconds * 500L;
        ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                if (System.currentTimeMillis() - lastEventSentAt.get() < quietEnoughMillis) {
                    return;
                }
                send(SseEventName.KEEP_ALIVE, payloadSupplier.get());
            } catch (Exception e) {
                // This only ends the ticker and the emitter: a disconnect noticed here cannot
                // unwind a thread blocked on an upstream socket, so the work itself should
                // probe the client instead, see probeClient.
                SseErrorHandler.handleError(e, contextId, emitter, this::cleanup);
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
