package au.org.aodn.ogcapi.server.core.exception;

import java.io.IOException;

/**
 * Raised when a write to an SSE client fails because the client has disconnected.
 * <p>
 * Streams learn this from the inside out: the write happens on the thread that is busy
 * reading an upstream response, so this exception is what unwinds that read and closes
 * the upstream connection. By the time a caller sees it, the upstream call has already
 * been abandoned — there is no result to deliver and nobody left to deliver it to, so
 * the only thing left to do is let it propagate.
 * <p>
 * It is an {@link IOException} so that {@code WfsErrorHandler} categorises it as a
 * client disconnect rather than as a failure of the work. Clients of a {@code RestTemplate}
 * will find it wrapped in a {@code ResourceAccessException} — see {@link #find}.
 */
public class SseClientGoneException extends IOException {

    public SseClientGoneException(String contextId, Throwable cause) {
        super("SSE client disconnected for " + contextId, cause);
    }

    /**
     * Find this exception in {@code throwable}'s cause chain, or null if it is not there.
     * {@code RestTemplate} wraps any {@link IOException} thrown by a response extractor in
     * a {@code ResourceAccessException}, so a disconnect that unwound an upstream read
     * always reaches the caller nested inside something else.
     */
    public static SseClientGoneException find(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SseClientGoneException clientGone) {
                return clientGone;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
