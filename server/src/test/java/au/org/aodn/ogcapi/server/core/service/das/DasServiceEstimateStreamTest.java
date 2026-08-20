package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.http.RecordingSseConnector;
import au.org.aodn.ogcapi.server.core.util.DasSseFrames;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the streamed cloud-optimised size estimate: the request DAS receives, the heartbeats
 * handed back as they arrive, and what happens when the caller's own client disappears
 * mid-estimate. A mocked WebClient cannot show any of that, so these tests drive a real one: a
 * stub connector for the frame-level assertions, and a real socket for the ones that only mean
 * something on the wire, that the request body arrives and that the connection to DAS is dropped.
 */
public class DasServiceEstimateStreamTest {

    private static final String HOST = "http://localhost:5000";

    private static final String HEARTBEAT_FRAME = """
            event: processing
            data: {"status":"processing","message":"Processing your request..."}
            
            """;

    private static final String RESULT_FRAME = """
            event: result
            data: {"status":"completed","data":{"estimated_output_bytes":123}}
            
            """;

    private RecordingSseConnector connector;
    private DasService dasService;

    private static DasProperties properties(String host, Duration sseIdleTimeout) {
        return new DasProperties(host, null, "test-secret", null,
                Duration.ofSeconds(5), Duration.ofSeconds(30), sseIdleTimeout);
    }

    private static DasService serviceOn(DasProperties properties, ClientHttpConnector connector) {
        WebClient webClient = WebClient.builder()
                .clientConnector(connector)
                .baseUrl(properties.host())
                .build();
        return new DasService(properties, new RestTemplate(), webClient, new ObjectMapper());
    }

    /**
     * Builds the service the way the application does, so the real connector is under test.
     */
    private static DasService realServiceOn(DasProperties properties) {
        return new DasService(properties, new RestTemplate(),
                new Config().createDasSseWebClient(properties, new ObjectMapper()), new ObjectMapper());
    }

    @BeforeEach
    public void setUp() {
        connector = new RecordingSseConnector();
        dasService = serviceOn(properties(HOST, Duration.ofMinutes(2)), connector);
    }

    private String estimate(DasSseFrames.FrameCallback onHeartbeat) {
        return estimate(dasService, onHeartbeat);
    }

    private static String estimate(DasService service, DasSseFrames.FrameCallback onHeartbeat) {
        return service.estimateCloudOptimisedDownloadSize(
                "test-uuid",
                Map.of("uuid", "test-uuid", "key", "a.zarr", "output_format", "netcdf"),
                onHeartbeat);
    }

    @Test
    public void testEstimateStreamsFramesAndUnwrapsTheResult() {
        connector.respondWith(List.of(HEARTBEAT_FRAME, HEARTBEAT_FRAME, RESULT_FRAME));

        AtomicInteger heartbeats = new AtomicInteger();
        String result = estimate(heartbeats::incrementAndGet);

        assertEquals("{\"estimated_output_bytes\":123}", result);
        assertEquals(2, heartbeats.get(), "Each DAS heartbeat is handed to the caller as it arrives");
    }

    @Test
    public void testEstimatePostsBatchStyleParametersAsJsonEventStream() {
        connector.respondWith(List.of(RESULT_FRAME));

        estimate(DasSseFrames.FrameCallback.IGNORE);

        assertEquals(HttpMethod.POST, connector.method());
        assertEquals(URI.create(HOST + "/api/v1/das/data/test-uuid/estimate_size"), connector.uri());
        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, connector.headers().getFirst(HttpHeaders.ACCEPT));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, connector.headers().getFirst(HttpHeaders.CONTENT_TYPE));

        String sent = connector.body();
        assertTrue(sent.contains("\"uuid\":\"test-uuid\""), "Got: " + sent);
        assertTrue(sent.contains("\"key\":\"a.zarr\""), "The batch-style parameters go to DAS unchanged: " + sent);
        assertTrue(sent.contains("\"output_format\":\"netcdf\""), "Got: " + sent);
    }

    /**
     * The body has to survive the connector, not just WebClient. A connector that forgets to
     * build a body publisher still opens the stream and still gets a 200, so nothing above this
     * level would notice DAS being sent an empty estimate request.
     */
    @Test
    @Timeout(30)
    public void testEstimateBodyReachesDasOverARealSocket() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(20_000);
            CompletableFuture<String> request = serveOneRequest(serverSocket, RESULT_FRAME);

            DasProperties properties = properties("http://localhost:" + serverSocket.getLocalPort(), Duration.ofSeconds(20));
            String result = estimate(realServiceOn(properties), DasSseFrames.FrameCallback.IGNORE);

            assertEquals("{\"estimated_output_bytes\":123}", result);

            String sent = request.get(20, TimeUnit.SECONDS);
            assertTrue(sent.startsWith("POST /api/v1/das/data/test-uuid/estimate_size "), "Got: " + sent);
            assertTrue(sent.contains("\"uuid\":\"test-uuid\""), "The request body must reach DAS: " + sent);
            assertTrue(sent.contains("\"key\":\"a.zarr\""), "Got: " + sent);
            assertTrue(sent.contains("\"output_format\":\"netcdf\""), "Got: " + sent);
        }
    }

    @Test
    public void testFailedWriteToTheClientAbandonsTheStreamMidBody() {
        // The client disconnects while DAS is still heartbeating: the write in the callback
        // throws, and that must abort the read instead of running the estimate to completion.
        List<String> frames = new ArrayList<>(Collections.nCopies(500, HEARTBEAT_FRAME));
        frames.add(RESULT_FRAME);
        connector.respondWith(frames);

        AtomicInteger heartbeats = new AtomicInteger();
        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> estimate(() -> {
            if (heartbeats.incrementAndGet() == 2) {
                throw new IOException("Broken pipe");
            }
        }));

        assertEquals("Broken pipe", e.getCause().getMessage());
        assertEquals(2, heartbeats.get(), "The read stops at the failed write");
        assertTrue(connector.wasCancelled(), "The response body must be cancelled, not left running");
        assertTrue(connector.framesDelivered() < frames.size(),
                "The body should be abandoned mid-stream, not drained to the end first");
    }

    @Test
    public void testErrorFrameStillSurfacesAsAnException() {
        // A failure raised after the stream opened arrives on an HTTP 200, so it is only visible
        // in the frames — the SSE layer must not report it as a successful estimate.
        connector.respondWith(List.of(HEARTBEAT_FRAME, """
                event: error
                data: {"status":"error","message":"404: No matching keys found for uuid=test-uuid"}
                
                """));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> estimate(DasSseFrames.FrameCallback.IGNORE));

        assertEquals("404: No matching keys found for uuid=test-uuid", e.getMessage(),
                "DAS's reason is forwarded verbatim for the SSE layer to report");
    }

    @Test
    public void testNon2xxPropagatesBeforeAnyFrameIsRead() {
        // Failures raised before the stream starts (auth, API not ready) are still HTTP errors.
        connector.respondWith(HttpStatus.NOT_FOUND);

        AtomicInteger heartbeats = new AtomicInteger();
        RuntimeException e = assertThrows(RuntimeException.class, () -> estimate(heartbeats::incrementAndGet));

        assertEquals(0, heartbeats.get());
        assertTrue(e.getMessage().contains("404"), "Got: " + e.getMessage());
        assertFalse(e.getMessage().contains(HOST),
                "WebClient quotes the request URL in its own message; the frontend shows this one: "
                        + e.getMessage());
    }

    /**
     * At socket level: a real DAS-shaped server that keeps
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

            DasProperties properties = properties("http://localhost:" + serverSocket.getLocalPort(), Duration.ofMinutes(20));
            DasService service = realServiceOn(properties);

            AtomicInteger heartbeats = new AtomicInteger();
            assertThrows(UncheckedIOException.class, () -> estimate(service, () -> {
                if (heartbeats.incrementAndGet() == 2) {
                    throw new IOException("Broken pipe");
                }
            }));

            assertTrue(sawDisconnect.get(20, TimeUnit.SECONDS),
                    "The server must see the connection close while it is still heartbeating");
        }
    }

    /**
     * sseIdleTimeout is a gap between frames. A DAS that opens the stream and then says nothing
     * is given up on, and the connection is closed rather than left pinning a worker thread.
     */
    @Test
    @Timeout(30)
    public void testIdleTimeoutGivesUpOnASilentDas() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(20_000);
            CompletableFuture<Boolean> sawDisconnect = serveHeadersThenSilence(serverSocket);

            DasProperties properties = properties("http://localhost:" + serverSocket.getLocalPort(), Duration.ofSeconds(1));
            DasService service = realServiceOn(properties);

            AtomicInteger heartbeats = new AtomicInteger();
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> estimate(service, heartbeats::incrementAndGet));

            assertEquals(0, heartbeats.get());
            assertNotNull(causeOfType(e, TimeoutException.class),
                    "A silent DAS should time out, got: " + e);
            assertTrue(sawDisconnect.get(20, TimeUnit.SECONDS),
                    "The abandoned stream must be closed, not left open");
        }
    }

    /**
     * The other half of the same semantics, and the regression the rename could have caused: a
     * DAS that keeps heartbeating is never cut off, however long it takes. This used to be a
     * ceiling on the whole exchange, which would kill an estimate that was working fine.
     */
    @Test
    @Timeout(30)
    public void testIdleTimeoutDoesNotCapAStreamThatKeepsHeartbeating() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(20_000);
            CompletableFuture<Boolean> sawDisconnect = serveHeartbeatsUntilClientLeaves(serverSocket, 100);

            DasProperties properties = properties("http://localhost:" + serverSocket.getLocalPort(), Duration.ofSeconds(1));
            DasService service = realServiceOn(properties);

            // Twenty heartbeats at 100ms apart is twice the idle timeout, so a whole-exchange
            // cap would have fired well before the client stops of its own accord.
            AtomicInteger heartbeats = new AtomicInteger();
            assertThrows(UncheckedIOException.class, () -> estimate(service, () -> {
                if (heartbeats.incrementAndGet() == 20) {
                    throw new IOException("Broken pipe");
                }
            }));

            assertEquals(20, heartbeats.get(), "A heartbeating stream must not be timed out");
            assertTrue(sawDisconnect.get(20, TimeUnit.SECONDS),
                    "The server must see the connection close");
        }
    }

    private static <T extends Throwable> T causeOfType(Throwable throwable, Class<T> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
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

                writeSseHeaders(out);

                // Keep heartbeating like a long estimate would, watching for the client to leave.
                for (int i = 0; i < 400; i++) {
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
            } catch (IOException e) {
                // A write failing because the peer has gone is the same news, by a shorter route.
                sawDisconnect.complete(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sse-test-server");

        server.setDaemon(true);
        server.start();
        return sawDisconnect;
    }

    /**
     * Opens the stream and then says nothing at all — a DAS that has stopped talking to us.
     * Completes with true once the client gives up and closes the connection.
     */
    private CompletableFuture<Boolean> serveHeadersThenSilence(ServerSocket serverSocket) {
        CompletableFuture<Boolean> sawDisconnect = new CompletableFuture<>();

        Thread server = new Thread(() -> {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(200);
                writeSseHeaders(socket.getOutputStream());

                // The first read hands back the request itself, so keep watching until the
                // client actually goes rather than reading once and calling it a day.
                InputStream in = socket.getInputStream();
                long deadline = System.currentTimeMillis() + 20_000;
                while (System.currentTimeMillis() < deadline) {
                    if (clientHasGone(in)) {
                        sawDisconnect.complete(true);
                        return;
                    }
                }
                sawDisconnect.complete(false);
            } catch (IOException e) {
                sawDisconnect.complete(true);
            }
        }, "sse-silent-test-server");

        server.setDaemon(true);
        server.start();
        return sawDisconnect;
    }

    /**
     * Reads one whole request — head and body — and answers it with sseBody, so a test can
     * assert on what actually went down the socket.
     */
    private CompletableFuture<String> serveOneRequest(ServerSocket serverSocket, String sseBody) {
        CompletableFuture<String> received = new CompletableFuture<>();

        Thread server = new Thread(() -> {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(20_000);
                String request = readRequest(socket.getInputStream());

                OutputStream out = socket.getOutputStream();
                writeSseHeaders(out);
                writeChunk(out, sseBody);
                out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();

                received.complete(request);
            } catch (IOException e) {
                received.completeExceptionally(e);
            }
        }, "sse-echo-test-server");

        server.setDaemon(true);
        server.start();
        return received;
    }

    /**
     * Reads the request head, then exactly as many body bytes as it declared.
     */
    private static String readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while (!endsWithBlankLine(head) && (b = in.read()) != -1) {
            head.write(b);
        }

        String text = head.toString(StandardCharsets.UTF_8);
        int contentLength = 0;
        for (String line : text.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        return text + new String(in.readNBytes(contentLength), StandardCharsets.UTF_8);
    }

    private static boolean endsWithBlankLine(ByteArrayOutputStream buffer) {
        byte[] bytes = buffer.toByteArray();
        return bytes.length >= 4
                && bytes[bytes.length - 4] == '\r' && bytes[bytes.length - 3] == '\n'
                && bytes[bytes.length - 2] == '\r' && bytes[bytes.length - 1] == '\n';
    }

    /**
     * Reads whatever the client has sent (its request, then nothing) to find out whether the
     * connection is still open. Reaching end of stream means it is not.
     */
    private static boolean clientHasGone(InputStream in) throws IOException {
        try {
            return in.read(new byte[8192]) == -1;
        } catch (SocketTimeoutException stillConnected) {
            return false;
        }
    }

    private static void writeSseHeaders(OutputStream out) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/event-stream\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void writeChunk(OutputStream out, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

}
