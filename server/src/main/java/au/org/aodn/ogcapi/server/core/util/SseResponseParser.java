package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * Extracts the payload of a data-access-service Server-Sent Events response.
 * DAS wraps long-running endpoints in its sse_it decorator, which keeps the connection alive
 * with processing heartbeats and then delivers the return value in a single final frame:
 * event: processing
 * data: {"status":"processing","message":"Processing your request..."}
 * event: result
 * data: {"status":"completed","message":"Done","data": { ...the actual payload... }}
 * Three things to know:
 * 1. Anything the endpoint throws arrives as a final error frame instead, still on an HTTP 200
 * because the stream has already started. So a failed call is only detectable by reading
 * the frames.
 * 2. This parser ONLY handles that single-final-frame shape. It does not handle the chunked
 * sse_wrapper responses DAS uses elsewhere, which emit many result frames to collect.
 * 3. Frames are consumed as they arrive rather than from a fully-buffered body, so the caller
 * gets a callback on every heartbeat.
 */
public final class SseResponseParser {

    private static final String EVENT_FIELD = "event";
    private static final String DATA_FIELD = "data";
    private static final String MESSAGE_FIELD = "message";

    private static final String RESULT_EVENT = "result";
    private static final String ERROR_EVENT = "error";

    private SseResponseParser() {
    }

    /**
     * Notified once per complete non-final frame, that is, once per DAS heartbeat.
     *
     * It is allowed to throw IOException on purpose. The caller forwards a keep-alive to its
     * own SSE client here, and a broken pipe from that write is the only way to learn the
     * client has gone. Letting it propagate aborts the read of the DAS stream and closes that
     * connection, which stops DAS working on a result nobody will read.
     */
    @FunctionalInterface
    public interface FrameCallback {

        /**
         * Does nothing. For callers reading a body that has already been buffered.
         */
        FrameCallback IGNORE = () -> {
        };

        void onFrame() throws IOException;
    }

    /**
     * Read an SSE body and return the payload nested under the final result frame's data
     * field, serialized as JSON.
     *
     * Throws RuntimeException if the stream carries an error frame, or if it ends without a
     * final frame. An error frame is rethrown with DAS's own message unchanged, so the caller
     * can forward it.
     */
    public static String extractResultData(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty response from data-access-service");
        }

        try {
            return extractResultData(objectMapper, new StringReader(body), FrameCallback.IGNORE);
        } catch (IOException e) {
            // Unreachable: reading an in-memory String cannot fail.
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Streaming form of the method above: reads frames off source as DAS emits them and calls
     * onHeartbeat after each complete non-final frame.
     *
     * It throws in two cases:
     * 1. IOException if the stream cannot be read, or if onHeartbeat throws. The caller's
     *    client has gone, so there is no point reading on.
     * 2. RuntimeException on an error frame, or a stream that ends without a final frame.
     *    Same contract as the buffered form.
     */
    public static String extractResultData(ObjectMapper objectMapper, Reader source, FrameCallback onHeartbeat)
            throws IOException {

        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);

        String event = null;
        StringBuilder data = new StringBuilder();
        boolean sawContent = false;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // Blank line terminates a frame.
                String payload = readTerminalFrame(objectMapper, event, data.toString());
                if (payload != null) {
                    return payload;
                }
                boolean completedFrame = event != null || !data.isEmpty();
                event = null;
                data.setLength(0);
                if (completedFrame) {
                    onHeartbeat.onFrame();
                }
                continue;
            }

            sawContent = true;
            if (line.startsWith(":")) {
                // Comment line, per the SSE spec.
                continue;
            }

            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            // A single leading space after the colon is part of the framing, not the value.
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }

            if (EVENT_FIELD.equals(field)) {
                event = value;
            } else if (DATA_FIELD.equals(field)) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(value);
            }
        }

        // The last frame may not be followed by a blank line.
        String payload = readTerminalFrame(objectMapper, event, data.toString());
        if (payload != null) {
            return payload;
        }

        if (!sawContent) {
            throw new RuntimeException("Empty response from data-access-service");
        }
        throw new RuntimeException("data-access-service stream ended without a result or error event");
    }

    /**
     * Interpret one complete frame. Returns the payload for a result frame, or null for a
     * frame that is not final (a heartbeat) and so should be skipped. Throws RuntimeException
     * for an error frame, or a result frame that carries no payload.
     */
    private static String readTerminalFrame(ObjectMapper objectMapper, String event, String data) {
        if (!RESULT_EVENT.equals(event) && !ERROR_EVENT.equals(event)) {
            return null;
        }

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
