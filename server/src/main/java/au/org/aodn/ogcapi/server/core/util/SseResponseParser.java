package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts the payload of a data-access-service Server-Sent Events response.
 * <p>
 * DAS wraps long-running endpoints in its {@code sse_it} decorator, which keeps the
 * connection alive with {@code processing} heartbeats and then delivers the return
 * value in a single terminal frame:
 * <pre>
 * event: processing
 * data: {"status":"processing","message":"Processing your request..."}
 *
 * event: result
 * data: {"status":"completed","message":"Done","data": { ...the actual payload... }}
 * </pre>
 * Anything the endpoint throws arrives as a terminal {@code error} frame instead —
 * on an HTTP 200, because the stream has already started, so a failed call is
 * <em>only</em> detectable by reading the frames.
 * <p>
 * This parser targets that single-terminal-frame shape. It is not suitable for the
 * chunked {@code sse_wrapper} responses DAS uses elsewhere, which emit many
 * {@code result} frames that all have to be collected.
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
     * Read an SSE body and return the payload nested under the terminal {@code result}
     * frame's {@code data} field, serialized as JSON.
     *
     * @throws RuntimeException if the stream carries an {@code error} frame (with DAS's
     *                          own message, unmodified, so the caller can forward it), or
     *                          if it ends without a terminal frame
     */
    public static String extractResultData(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty response from data-access-service");
        }

        String event = null;
        StringBuilder data = new StringBuilder();

        for (String line : body.lines().toList()) {
            if (line.isEmpty()) {
                // Blank line terminates a frame.
                String payload = readTerminalFrame(objectMapper, event, data.toString());
                if (payload != null) {
                    return payload;
                }
                event = null;
                data.setLength(0);
                continue;
            }
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
            }
            else if (DATA_FIELD.equals(field)) {
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

        throw new RuntimeException("data-access-service stream ended without a result or error event");
    }

    /**
     * Interpret one complete frame.
     *
     * @return the payload for a {@code result} frame, or null for any frame that is not
     * terminal (heartbeats) and so should be skipped
     * @throws RuntimeException for an {@code error} frame, or a {@code result} frame that
     *                          carries no payload
     */
    private static String readTerminalFrame(ObjectMapper objectMapper, String event, String data) {
        if (!RESULT_EVENT.equals(event) && !ERROR_EVENT.equals(event)) {
            return null;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        }
        catch (Exception e) {
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
