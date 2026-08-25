package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;

import java.io.IOException;

/**
 * Reads the data-access-service Server-Sent Events contract. Splitting the stream into frames is
 * the HTTP client's job; what is left here is what DAS means by a frame.
 * DAS wraps long-running endpoints in its sse_it decorator, which heartbeats while it works and
 * then returns the value in a single final frame:
 * event: processing
 * data: {"status":"processing","message":"Processing your request..."}
 * event: result
 * data: {"status":"completed","message":"Done","data": { ...the actual payload... }}
 * Two things to know:
 * 1. Anything the endpoint throws arrives as a final error frame instead, still on an HTTP 200
 * because the stream has already started, so a failed call shows up only by reading the frames.
 * 2. This handles only that single-final-frame shape, not the chunked sse_wrapper responses DAS
 * uses elsewhere, which emit many result frames to collect.
 */
public final class DasSseFrames {

    private static final String DATA_FIELD = "data";
    private static final String MESSAGE_FIELD = "message";

    private static final String RESULT_EVENT = "result";
    private static final String ERROR_EVENT = "error";

    private DasSseFrames() {
    }

    /**
     * Notified once per non-final frame, that is, once per DAS heartbeat.
     * It may throw IOException on purpose: the caller forwards a keep-alive to its own SSE client
     * here, and a broken pipe from that write is the only way to learn the client has gone.
     * Letting it propagate aborts the read of the DAS stream and closes that connection, so DAS
     * stops working on a result nobody will read.
     */
    @FunctionalInterface
    public interface FrameCallback {

        /**
         * Does nothing. For callers with no client of their own to keep alive.
         */
        FrameCallback IGNORE = () -> {
        };

        void onFrame() throws IOException;
    }

    /**
     * Interpret one frame. Returns a result frame's nested data payload as JSON, or null for a
     * heartbeat the caller should skip. Throws RuntimeException for an error frame, or a result
     * frame with no payload; an error keeps DAS's own message so the caller can forward it.
     */
    public static String readTerminalFrame(ObjectMapper objectMapper, ServerSentEvent<String> frame) {
        String event = frame.event();
        if (!RESULT_EVENT.equals(event) && !ERROR_EVENT.equals(event)) {
            return null;
        }

        // A frame with no data line at all reads as an empty document, same as before.
        String data = frame.data() == null ? "" : frame.data();
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Unreadable data-access-service %s event: %s", event, data), e);
        }

        if (ERROR_EVENT.equals(event)) {
            JsonNode message = node.get(MESSAGE_FIELD);
            // Rethrow the reason verbatim: callers prefix it with their own context, and
            // DAS already prefixes it with the status it would have returned.
            throw new RuntimeException(message != null && !message.isNull() ?
                    message.asText() :
                    "data-access-service reported an error with no message");
        }

        JsonNode payload = node.get(DATA_FIELD);
        if (payload == null || payload.isNull()) {
            throw new RuntimeException("data-access-service result event carried no data: " + data);
        }
        return payload.toString();
    }
}
