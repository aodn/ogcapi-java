package au.org.aodn.ogcapi.server.core.exception;

import java.io.IOException;

/**
 * Raised when a write to an SSE client fails because the client has disconnected.
 */
public class SseClientGoneException extends IOException {

    public SseClientGoneException(String contextId, Throwable cause) {
        super("SSE client disconnected for " + contextId, cause);
    }

    /**
     * Find this exception in {@code throwable}'s cause chain, or null if it is not there.
     * A disconnect that unwound an upstream read always reaches the caller nested inside
     * something else: {@code RestTemplate} wraps it in a {@code ResourceAccessException}, and
     * the streamed DAS estimate wraps it in an {@code UncheckedIOException}.
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
