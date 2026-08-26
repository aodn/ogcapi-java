package au.org.aodn.ogcapi.server.core.http;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Hands a WebClient a canned SSE response and keeps what it was asked to send.
 * A WebClient has no interceptors and no MockRestServiceServer, so the connector is the only place
 * a test can see the request. This stands in for one and hands back frames one at a time, so a
 * test can also tell whether the body was cancelled or read to the end.
 */
public final class RecordingSseConnector implements ClientHttpConnector {

    private static final DataBufferFactory BUFFERS = DefaultDataBufferFactory.sharedInstance;

    private HttpStatusCode status = HttpStatus.OK;
    private List<String> frames = List.of();
    private MockClientHttpRequest lastRequest;
    private String lastBody;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicInteger framesDelivered = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();

    /**
     * Answer with 200 and these SSE frames, each one a complete frame ending in a blank line.
     */
    public RecordingSseConnector respondWith(List<String> frames) {
        this.status = HttpStatus.OK;
        this.frames = frames;
        return this;
    }

    /**
     * Answer with a status and no body, the way DAS fails before a stream opens.
     */
    public RecordingSseConnector respondWith(HttpStatusCode status) {
        this.status = status;
        this.frames = List.of();
        return this;
    }

    /**
     * Answer with a status and this body, the way a gateway answers on behalf of a DAS that is
     * down: the body is its own error page, not anything DAS wrote.
     */
    public RecordingSseConnector respondWith(HttpStatusCode status, String body) {
        this.status = status;
        this.frames = List.of(body);
        return this;
    }

    public HttpMethod method() {
        return lastRequest.getMethod();
    }

    public URI uri() {
        return lastRequest.getURI();
    }

    public HttpHeaders headers() {
        return lastRequest.getHeaders();
    }

    public String body() {
        return lastBody;
    }

    public boolean wasCancelled() {
        return cancelled.get();
    }

    public int framesDelivered() {
        return framesDelivered.get();
    }

    /**
     * How many times a WebClient asked to connect, so a test can tell a real call from a
     * cached one.
     */
    public int requests() {
        return requests.get();
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
                                            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

        MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
        lastRequest = request;
        requests.incrementAndGet();

        return requestCallback.apply(request)
                // Deferred: the mock only has a body once the callback above has written one.
                .then(Mono.defer(request::getBodyAsString))
                .doOnNext(body -> lastBody = body)
                .then(Mono.fromSupplier(this::response));
    }

    private ClientHttpResponse response() {
        MockClientHttpResponse response = new MockClientHttpResponse(status);
        response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        // One buffer per frame, handed over only as the reader asks for it, so a cancel shows
        // up as frames that were never delivered.
        response.setBody(Flux.fromIterable(frames)
                .doOnNext(frame -> framesDelivered.incrementAndGet())
                .map(frame -> BUFFERS.wrap(frame.getBytes(StandardCharsets.UTF_8)))
                .doOnCancel(() -> cancelled.set(true)));
        return response;
    }
}
