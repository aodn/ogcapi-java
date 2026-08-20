package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What DAS means by a frame. These mirror what its sse_it decorator emits, including the error
 * frames that arrive on an HTTP 200 instead of an error status. Splitting frames out of the
 * stream is the HTTP client's job and is covered by DasServiceEstimateStreamTest.
 */
public class DasSseFramesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ServerSentEvent<String> frame(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private String read(String event, String data) {
        return DasSseFrames.readTerminalFrame(objectMapper, frame(event, data));
    }

    @Test
    public void testResultFrameReturnsNestedData() {
        assertEquals("{\"uuid\":\"abc\",\"estimated_output_bytes\":123}",
                read("result", "{\"status\":\"completed\",\"message\":\"Done\",\"data\":{\"uuid\":\"abc\",\"estimated_output_bytes\":123}}"),
                "The estimate dict nested under the result event's data field should be returned");
    }

    @Test
    public void testLargeByteCountSurvivesUnchanged() {
        // The estimate is a Python int with no width limit, so it must not be routed
        // through a lossy numeric type on the way out.
        String payload = read("result", "{\"status\":\"completed\",\"data\":{\"estimated_output_bytes\":9007199254740993}}");

        assertTrue(payload.contains("9007199254740993"), "A byte count beyond 2^53 must not lose precision");
    }

    @Test
    public void testHeartbeatIsSkipped() {
        // A slow estimate heartbeats until the work finishes; null tells the caller to read on.
        assertNull(read("processing", "{\"status\":\"processing\",\"message\":\"Processing your request...\"}"));
    }

    @Test
    public void testFrameWithNoEventNameIsSkipped() {
        assertNull(read(null, "{\"status\":\"processing\"}"));
    }

    @Test
    public void testErrorFrameThrowsWithDasMessageVerbatim() {
        // What a "no matching keys" failure looks like now the route raises inside the
        // stream: HTTP 200, and Starlette's HTTPException.__str__ supplies the "404: ".
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                read("error", "{\"status\":\"error\",\"message\":\"404: No matching keys found for uuid=abc, keys=['missing.zarr']\"}"));

        assertEquals("404: No matching keys found for uuid=abc, keys=['missing.zarr']", e.getMessage(),
                "DAS's reason must be rethrown unmodified; callers add their own context");
    }

    @Test
    public void testErrorFrameWithoutAMessageStillThrows() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> read("error", "{\"status\":\"error\"}"));

        assertTrue(e.getMessage().contains("no message"), "Got: " + e.getMessage());
    }

    @Test
    public void testResultFrameWithoutDataThrows() {
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                read("result", "{\"status\":\"completed\",\"message\":\"Done\"}"));

        assertTrue(e.getMessage().contains("carried no data"), "Got: " + e.getMessage());
    }

    @Test
    public void testResultFrameWithNullDataThrows() {
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                read("result", "{\"status\":\"completed\",\"data\":null}"));

        assertTrue(e.getMessage().contains("carried no data"), "Got: " + e.getMessage());
    }

    @Test
    public void testUnreadableTerminalFrameThrows() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> read("result", "{\"status\":\"comp"));

        assertTrue(e.getMessage().contains("Unreadable"), "Got: " + e.getMessage());
    }

    @Test
    public void testTerminalFrameWithNoDataAtAllThrows() {
        // A result event with no data line reads as an empty document, not as a heartbeat.
        assertThrows(RuntimeException.class, () -> read("result", null));
    }
}
