package au.org.aodn.ogcapi.server.core.exception.wfs;

import au.org.aodn.ogcapi.server.core.exception.GeoserverFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException;
import au.org.aodn.ogcapi.server.core.util.TestLogAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WfsErrorHandlerTest {

    protected TestLogAppender logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = TestLogAppender.attachTo(WfsErrorHandler.class);
    }

    @AfterEach
    void detachLogAppender() {
        logAppender.detachFrom(WfsErrorHandler.class);
    }

    /**
     * An upstream WFS server error status must be logged at ERROR level (so New
     * Relic can pick it up), sent to the client as an SSE error event, and must
     * not be mistaken for a client disconnect.
     */
    @Test
    void verifyUpstreamServerErrorLoggedAtErrorLevel() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        WfsErrorHandler.handleError(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                "uuid-123", emitter, null);

        List<LogEvent> errors = logAppender.eventsAtLevel(Level.ERROR);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().getFormattedMessage().contains("uuid-123"));
        assertNotNull(errors.get(0).getThrown(), "stack trace expected in error log");

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).completeWithError(any());
    }

    /**
     * An unexpected exception must be logged at ERROR level too.
     */
    @Test
    void verifyUnknownErrorLoggedAtErrorLevel() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        WfsErrorHandler.handleError(new RuntimeException("boom"), "uuid-123", emitter, null);

        assertEquals(1, logAppender.eventsAtLevel(Level.ERROR).size());
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).completeWithError(any());
    }

    /**
     * A WFS server that is not authorized must be logged at WARN with the
     * exception attached (its message names the server) and reported to the
     * client, without raising an ERROR alert.
     */
    @Test
    void verifyUnauthorizedServerLoggedAtWarnLevel() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        WfsErrorHandler.handleError(
                new UnauthorizedServerException("Server http://not-allowed/wfs is not authorized"),
                "uuid-123", emitter, null);

        assertTrue(logAppender.eventsAtLevel(Level.ERROR).isEmpty());
        List<LogEvent> warns = logAppender.eventsAtLevel(Level.WARN);
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).getMessage().getFormattedMessage().contains("uuid-123"));
        assertNotNull(warns.get(0).getThrown(), "exception expected in log for server identification");

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).completeWithError(any());
    }

    /**
     * No downloadable fields found must be logged at WARN with the exception
     * attached and reported to the client, without raising an ERROR alert; the
     * per-server failures behind it are logged separately by WfsServer.
     */
    @Test
    void verifyDownloadableFieldsNotFoundLoggedAtWarnLevel() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);

        WfsErrorHandler.handleError(
                new GeoserverFieldsNotFoundException("No downloadable fields found for all url"),
                "uuid-123", emitter, null);

        assertTrue(logAppender.eventsAtLevel(Level.ERROR).isEmpty());
        List<LogEvent> warns = logAppender.eventsAtLevel(Level.WARN);
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).getMessage().getFormattedMessage().contains("uuid-123"));
        assertNotNull(warns.get(0).getThrown(), "exception expected in log");

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).completeWithError(any());
    }

    /**
     * A client disconnect stays at WARN level so New Relic error alerting is not
     * flooded with failures that are not server problems.
     */
    @Test
    void verifyClientDisconnectStaysAtWarnLevel() {
        SseEmitter emitter = mock(SseEmitter.class);

        WfsErrorHandler.handleError(new IOException("Broken pipe"), "uuid-123", emitter, null);

        assertTrue(logAppender.eventsAtLevel(Level.ERROR).isEmpty());
        assertEquals(1, logAppender.eventsAtLevel(Level.WARN).size());
        verify(emitter, times(1)).completeWithError(any());
    }
}
