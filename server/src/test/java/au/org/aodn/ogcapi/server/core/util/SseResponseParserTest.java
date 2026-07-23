package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the data-access-service SSE body parser. The frames used here mirror
 * what DAS's {@code sse_it} decorator emits ({@code format_sse} writes
 * {@code "event: <name>\ndata: <json>\n\n"}), including the error frames that now
 * arrive on an HTTP 200 in place of the status codes the endpoint used to return.
 */
public class SseResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String parse(String body) {
        return SseResponseParser.extractResultData(objectMapper, body);
    }

    @Test
    public void testResultFrameReturnsNestedData() {
        String body = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: result
                data: {"status":"completed","message":"Done","data":{"uuid":"abc","estimated_output_bytes":123}}

                """;

        assertEquals("{\"uuid\":\"abc\",\"estimated_output_bytes\":123}", parse(body),
                "The estimate dict nested under the result event's data field should be returned");
    }

    @Test
    public void testLargeByteCountSurvivesUnchanged() {
        // The estimate is a Python int with no width limit, so it must not be routed
        // through a lossy numeric type on the way out.
        String body = """
                event: result
                data: {"status":"completed","data":{"estimated_output_bytes":9007199254740993}}

                """;

        assertTrue(parse(body).contains("9007199254740993"), "A byte count beyond 2^53 must not lose precision");
    }

    @Test
    public void testHeartbeatsBeforeResultAreSkipped() {
        // A slow estimate heartbeats every 30s until the work finishes.
        String body = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: processing
                data: {"status":"processing","message":"Still processing..."}

                event: processing
                data: {"status":"processing","message":"Still processing..."}

                event: result
                data: {"status":"completed","message":"Done","data":{"estimated_output_bytes":1}}

                """;

        assertEquals("{\"estimated_output_bytes\":1}", parse(body));
    }

    @Test
    public void testErrorFrameThrowsWithDasMessageVerbatim() {
        // What a "no matching keys" failure looks like now the route raises inside the
        // stream: HTTP 200, and Starlette's HTTPException.__str__ supplies the "404: ".
        String body = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: error
                data: {"status":"error","message":"404: No matching keys found for uuid=abc, keys=['missing.zarr']"}

                """;

        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(body));
        assertEquals("404: No matching keys found for uuid=abc, keys=['missing.zarr']", e.getMessage(),
                "DAS's reason must be rethrown unmodified; callers add their own context");
    }

    @Test
    public void testHeartbeatOnlyStreamThrows() {
        // The connection dropped before the estimate finished.
        String body = """
                event: processing
                data: {"status":"processing","message":"Processing your request..."}

                event: processing
                data: {"status":"processing","message":"Still processing..."}

                """;

        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(body));
        assertTrue(e.getMessage().contains("without a result or error event"), "Got: " + e.getMessage());
    }

    @Test
    public void testTruncatedStreamThrows() {
        String body = "event: processing\ndata: {\"status\":\"proce";

        assertThrows(RuntimeException.class, () -> parse(body));
    }

    @Test
    public void testResultFrameWithoutDataThrows() {
        String body = """
                event: result
                data: {"status":"completed","message":"Done"}

                """;

        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(body));
        assertTrue(e.getMessage().contains("carried no data"), "Got: " + e.getMessage());
    }

    @Test
    public void testTerminalFrameNotFollowedByBlankLineIsStillRead() {
        String body = "event: result\ndata: {\"status\":\"completed\",\"data\":{\"estimated_output_bytes\":7}}";

        assertEquals("{\"estimated_output_bytes\":7}", parse(body));
    }

    @Test
    public void testCarriageReturnLineEndingsAreHandled() {
        String body = "event: processing\r\ndata: {\"status\":\"processing\"}\r\n" +
                "\r\n" +
                "event: result\r\ndata: {\"status\":\"completed\",\"data\":{\"estimated_output_bytes\":7}}\r\n\r\n";

        assertEquals("{\"estimated_output_bytes\":7}", parse(body));
    }

    @Test
    public void testCommentLinesAreIgnored() {
        String body = """
                : this is a keep-alive comment

                event: result
                data: {"status":"completed","data":{"estimated_output_bytes":7}}

                """;

        assertEquals("{\"estimated_output_bytes\":7}", parse(body));
    }

    @Test
    public void testNonSseBodyThrows() {
        // The endpoint only speaks SSE now; a bare JSON body has no terminal frame.
        String body = "{\"uuid\":\"abc\",\"estimated_output_bytes\":123}";

        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(body));
        assertTrue(e.getMessage().contains("without a result or error event"), "Got: " + e.getMessage());
    }

    @Test
    public void testEmptyBodyThrows() {
        assertThrows(RuntimeException.class, () -> parse(""));
        assertThrows(RuntimeException.class, () -> parse(null));
    }
}
