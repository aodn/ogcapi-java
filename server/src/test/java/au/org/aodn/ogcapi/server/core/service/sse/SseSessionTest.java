package au.org.aodn.ogcapi.server.core.service.sse;

import au.org.aodn.ogcapi.server.core.exception.SseClientGoneException;
import au.org.aodn.ogcapi.server.core.model.enumeration.SseEventName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * The keep-alive ticker, which is what stops an idle timeout in front of this service dropping a
 * stream that is waiting on a slow upstream server, and the probe it shares the stream with.
 */
public class SseSessionTest {

    private final List<String> written = Collections.synchronizedList(new ArrayList<>());

    private SseSession sessionWritingTo(List<String> events) {
        SseEmitter emitter = mock(SseEmitter.class);
        try {
            doAnswer(invocation -> {
                events.add(render(invocation.getArgument(0)));
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new SseSession("test-uuid", emitter);
    }

    private static String render(SseEmitter.SseEventBuilder builder) {
        return builder.build().stream()
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining());
    }

    /**
     * Only what the browser would surface: a named keep-alive event, not a probe's comment line.
     */
    private long keepAliveEvents() {
        return List.copyOf(written).stream().filter(event -> event.contains("event:keep-alive")).count();
    }

    private long probes() {
        return List.copyOf(written).stream().filter(event -> event.startsWith(":probe")).count();
    }

    /**
     * Work that already writes events to its client should not have the ticker's events on top of
     * its own. The connection is busy either way, which is the whole point of the ticker.
     */
    @Test
    @Timeout(20)
    public void testTicksAreSkippedWhileTheWorkIsWritingItsOwnEvents() throws Exception {
        SseSession session = sessionWritingTo(written);
        session.startKeepAlive(1, () -> Map.of("message", "waiting"));

        try {
            // Six events of our own, 250ms apart, so the tick at one second finds a stream that
            // was written to a moment ago.
            for (int i = 0; i < 6; i++) {
                session.send(SseEventName.CONNECTION_ESTABLISHED, Map.of("n", i));
                Thread.sleep(250);
            }

            assertEquals(0, keepAliveEvents(),
                    "A stream its own work is writing to does not need the ticker: " + written);

            // Nothing else is written from here, so the ticker takes over.
            long deadline = System.currentTimeMillis() + 5000;
            while (keepAliveEvents() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            assertTrue(keepAliveEvents() > 0, "A stream that has gone quiet must be kept alive: " + written);
        } finally {
            session.cleanup();
        }
    }

    /**
     * The other half: when the ticker is the only thing writing, every tick must send. Skipping
     * on "something was sent within the interval" would have skipped every second tick, since
     * the ticker's own send lands just after the tick it belongs to.
     */
    @Test
    @Timeout(20)
    public void testEveryTickSendsWhenTheTickerIsTheOnlyWriter() throws Exception {
        SseSession session = sessionWritingTo(written);
        session.startKeepAlive(1, () -> Map.of("message", "waiting"));

        try {
            long deadline = System.currentTimeMillis() + 8000;
            while (keepAliveEvents() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            assertTrue(keepAliveEvents() >= 3,
                    "Three ticks in about three seconds, got " + keepAliveEvents() + ": " + written);
        } finally {
            session.cleanup();
        }
    }

    /**
     * The regression this guards: probing on a schedule of its own, which is what the
     * cloud-optimised estimate does with the data-access-service heartbeat, used to send a
     * keep-alive event. A probe landing just after a tick is too old by the next tick to skip it,
     * so the two never got out of each other's way and the browser saw every keep-alive twice.
     * A probe writes a comment now, so it cannot be mistaken for an event and cannot silence the
     * ticker: the client reads one keep-alive per interval, no more and no fewer.
     */
    @Test
    @Timeout(20)
    public void testProbesAreInvisibleToTheClientAndDoNotDisturbTheTicker() throws Exception {
        SseSession session = sessionWritingTo(written);
        session.startKeepAlive(1, () -> Map.of("message", "waiting"));

        try {
            // Probe at the rate the ticker runs at, the phase that used to duplicate: DAS
            // heartbeats every interval, each arriving a moment after a tick.
            Thread.sleep(1100);
            for (int i = 0; i < 3; i++) {
                session.probeClient();
                Thread.sleep(1000);
            }

            assertEquals(3, probes(), "Every probe should reach the client: " + written);
            assertTrue(keepAliveEvents() >= 3,
                    "Probes must not silence the ticker, got " + keepAliveEvents() + ": " + written);
            assertTrue(keepAliveEvents() <= 5,
                    "Probes must not add keep-alive events of their own, got " + keepAliveEvents()
                            + ": " + written);
        } finally {
            session.cleanup();
        }
    }

    /**
     * What the probe is for: the write is the only way to learn the client has gone, so a broken
     * pipe has to come back as the disconnect it is rather than as a plain IOException.
     */
    @Test
    public void testProbeReportsABrokenPipeAsAGoneClient() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        SseSession session = new SseSession("test-uuid", emitter);

        SseClientGoneException e = assertThrows(SseClientGoneException.class, session::probeClient);

        assertTrue(e.getMessage().contains("test-uuid"), "Got: " + e.getMessage());
    }
}
