package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.util.SseResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the streamed cloud-optimised size estimate: the request DAS receives, the heartbeats
 * handed back to the caller as they arrive, and — the point of streaming it at all — what happens
 * when the caller's own client disappears mid-estimate.
 * <p>
 * A mocked RestTemplate cannot show any of that, so these tests drive a real one: a stub request
 * factory for the frame-level assertions, and a real socket for the assertion that matters most,
 * that the connection to DAS is dropped rather than left running.
 */
public class DasServiceEstimateStreamTest {

    private static final String HOST = "http://localhost:5000";

    private static final String HEARTBEAT_FRAME = """
            event: processing
            data: {"status":"processing","message":"Processing your request..."}

            """;

    private RecordingBody body;
    private StubRequestFactory requestFactory;
    private DasService dasService;

    @BeforeEach
    public void setUp() {
        DasProperties properties = new DasProperties(
                HOST, null, "test-secret", null,
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(20));

        requestFactory = new StubRequestFactory();
        RestTemplate sseTemplate = new RestTemplate(requestFactory);
        dasService = new DasService(properties, new RestTemplate(), sseTemplate, new ObjectMapper());
    }

    private void respondWith(String sseBody) {
        body = new RecordingBody(sseBody);
        requestFactory.responder = () -> new MockClientHttpResponse(body, HttpStatus.OK);
    }

    private String estimate(SseResponseParser.FrameCallback onHeartbeat) {
        return dasService.estimateCloudOptimisedDownloadSize(
                "test-uuid",
                Map.of("uuid", "test-uuid", "key", "a.zarr", "output_format", "netcdf"),
                onHeartbeat);
    }

    @Test
    public void testEstimateStreamsFramesAndUnwrapsTheResult() {
        respondWith(HEARTBEAT_FRAME + HEARTBEAT_FRAME + """
                event: result
                data: {"status":"completed","data":{"estimated_output_bytes":123}}

                """);

        AtomicInteger heartbeats = new AtomicInteger();
        String result = estimate(heartbeats::incrementAndGet);

        assertEquals("{\"estimated_output_bytes\":123}", result);
        assertEquals(2, heartbeats.get(), "Each DAS heartbeat is handed to the caller as it arrives");
    }

    @Test
    public void testEstimatePostsBatchStyleParametersAsJsonEventStream() {
        respondWith("""
                event: result
                data: {"status":"completed","data":{"estimated_output_bytes":123}}

                """);

        estimate(SseResponseParser.FrameCallback.IGNORE);

        MockClientHttpRequest request = requestFactory.lastRequest;
        assertEquals(HttpMethod.POST, request.getMethod());
        assertEquals(URI.create(HOST + "/api/v1/das/data/test-uuid/estimate_size"), request.getURI());
        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, request.getHeaders().getFirst(HttpHeaders.ACCEPT));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

        String sent = request.getBodyAsString();
        assertTrue(sent.contains("\"uuid\":\"test-uuid\""), "Got: " + sent);
        assertTrue(sent.contains("\"key\":\"a.zarr\""), "The batch-style parameters go to DAS unchanged: " + sent);
        assertTrue(sent.contains("\"output_format\":\"netcdf\""), "Got: " + sent);
    }

    @Test
    public void testFailedWriteToTheClientAbandonsTheStreamMidBody() {
        // The client disconnects while DAS is still heartbeating: the write in the callback
        // throws, and that must abort the read instead of running the estimate to completion.
        respondWith(HEARTBEAT_FRAME.repeat(500) + """
                event: result
                data: {"status":"completed","data":{"estimated_output_bytes":123}}

                """);

        AtomicInteger heartbeats = new AtomicInteger();
        ResourceAccessException e = assertThrows(ResourceAccessException.class, () -> estimate(() -> {
            if (heartbeats.incrementAndGet() == 2) {
                throw new IOException("Broken pipe");
            }
        }));

        // RestTemplate wraps an IOException from a response extractor, so callers see it nested.
        assertInstanceOf(IOException.class, e.getCause());
        assertEquals("Broken pipe", e.getCause().getMessage());
        assertEquals(2, heartbeats.get(), "The read stops at the failed write");
        assertTrue(body.closed, "The response body must be closed, not left open");
        assertTrue(body.remainingAtClose > 0,
                "The body should be abandoned mid-stream, not drained to the end first");
    }

    @Test
    public void testErrorFrameStillSurfacesAsAnException() {
        // A failure raised after the stream opened arrives on an HTTP 200, so it is only visible
        // in the frames — the SSE layer must not report it as a successful estimate.
        respondWith(HEARTBEAT_FRAME + """
                event: error
                data: {"status":"error","message":"404: No matching keys found for uuid=test-uuid"}

                """);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> estimate(SseResponseParser.FrameCallback.IGNORE));

        assertEquals("404: No matching keys found for uuid=test-uuid", e.getMessage(),
                "DAS's reason is forwarded verbatim for the SSE layer to report");
    }

    @Test
    public void testNon2xxPropagatesBeforeAnyFrameIsRead() {
        // Failures raised before the stream starts (auth, API not ready) are still HTTP errors.
        requestFactory.responder = () -> new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND);

        AtomicInteger heartbeats = new AtomicInteger();
        assertThrows(HttpClientErrorException.class, () -> estimate(heartbeats::incrementAndGet));
        assertEquals(0, heartbeats.get());
    }

    /**
     * The whole point of the fix, at socket level: a real DAS-shaped server that keeps
     * heartbeating, a client that goes away, and the assertion that the connection is dropped
     * while the server still thinks it has work to do. Left unclosed, DAS would keep computing
     * an estimate nobody will read.
     */
    @Test
    @Timeout(30)
    public void testUpstreamSocketIsClosedWhenTheClientGoesAway() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(20_000);
            CompletableFuture<Boolean> sawDisconnect = serveHeartbeatsUntilClientLeaves(serverSocket, 0);

            DasProperties properties = new DasProperties(
                    "http://localhost:" + serverSocket.getLocalPort(), null, "test-secret", null,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(20));
            RestTemplate sseTemplate = new Config().createDasSseRestTemplate(properties);
            DasService service = new DasService(
                    properties, new RestTemplate(), sseTemplate, new ObjectMapper());

            AtomicInteger heartbeats = new AtomicInteger();
            assertThrows(ResourceAccessException.class, () -> service.estimateCloudOptimisedDownloadSize(
                    "test-uuid",
                    Map.of("uuid", "test-uuid", "output_format", "netcdf"),
                    () -> {
                        if (heartbeats.incrementAndGet() == 2) {
                            throw new IOException("Broken pipe");
                        }
                    }));

            assertTrue(sawDisconnect.get(20, TimeUnit.SECONDS),
                    "The server must see the connection close while it is still heartbeating");
        }
    }

    /**
     * {@code sseReadTimeout} caps the whole exchange, it is not an idle timeout — an easy thing
     * to get wrong when tuning it, and getting it wrong kills estimates that were working. Here
     * DAS heartbeats continuously and the stream is still cut off at the cap.
     */
    @Test
    @Timeout(30)
    public void testSseReadTimeoutCapsTheWholeStreamNotJustIdleTime() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(20_000);
            CompletableFuture<Boolean> sawDisconnect = serveHeartbeatsUntilClientLeaves(serverSocket, 100);

            DasProperties properties = new DasProperties(
                    "http://localhost:" + serverSocket.getLocalPort(), null, "test-secret", null,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(1));
            RestTemplate sseTemplate = new Config().createDasSseRestTemplate(properties);
            DasService service = new DasService(
                    properties, new RestTemplate(), sseTemplate, new ObjectMapper());

            AtomicInteger heartbeats = new AtomicInteger();
            assertThrows(ResourceAccessException.class, () -> service.estimateCloudOptimisedDownloadSize(
                    "test-uuid",
                    Map.of("uuid", "test-uuid", "output_format", "netcdf"),
                    heartbeats::incrementAndGet));

            assertTrue(heartbeats.get() > 1,
                    "DAS was heartbeating throughout, so no idle timeout could have fired: got "
                            + heartbeats.get() + " heartbeats");
            assertTrue(sawDisconnect.get(20, TimeUnit.SECONDS),
                    "The capped stream must be closed, not left open");
        }
    }

    /**
     * Answers one request with an endless SSE heartbeat stream — DAS mid-estimate — and completes
     * with true as soon as the client's end of the connection goes away.
     */
    private CompletableFuture<Boolean> serveHeartbeatsUntilClientLeaves(ServerSocket serverSocket, long pauseMillis) {
        CompletableFuture<Boolean> sawDisconnect = new CompletableFuture<>();

        Thread server = new Thread(() -> {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(200);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Transfer-Encoding: chunked\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // Keep heartbeating like a long estimate would, watching for the client to leave.
                for (int i = 0; i < 100; i++) {
                    writeChunk(out, HEARTBEAT_FRAME);
                    if (clientHasGone(in)) {
                        sawDisconnect.complete(true);
                        return;
                    }
                    if (pauseMillis > 0) {
                        Thread.sleep(pauseMillis);
                    }
                }
                sawDisconnect.complete(false);
            }
            catch (IOException e) {
                // A write failing because the peer has gone is the same news, by a shorter route.
                sawDisconnect.complete(true);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sse-test-server");

        server.setDaemon(true);
        server.start();
        return sawDisconnect;
    }

    /**
     * Reads whatever the client has sent (its request, then nothing) to find out whether the
     * connection is still open. Reaching end of stream means it is not.
     */
    private static boolean clientHasGone(InputStream in) throws IOException {
        try {
            return in.read(new byte[8192]) == -1;
        }
        catch (SocketTimeoutException stillConnected) {
            return false;
        }
    }

    private static void writeChunk(OutputStream out, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /**
     * Hands the RestTemplate a canned response and keeps the request it wrote.
     */
    private static final class StubRequestFactory implements ClientHttpRequestFactory {

        private Supplier<ClientHttpResponse> responder;
        private MockClientHttpRequest lastRequest;

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            MockClientHttpRequest request = new MockClientHttpRequest(httpMethod, uri);
            request.setResponse(responder.get());
            lastRequest = request;
            return request;
        }
    }

    /**
     * An SSE body that remembers how it was finished with: closed, and with how much left unread.
     */
    private static final class RecordingBody extends ByteArrayInputStream {

        private boolean closed;
        private int remainingAtClose;

        private RecordingBody(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            // ByteArrayInputStream.close() is a no-op, so there is nothing to delegate to;
            // what matters is that the reader closed it, and how much it left unread.
            if (!closed) {
                closed = true;
                remainingAtClose = available();
            }
        }
    }
}
