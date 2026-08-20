package au.org.aodn.ogcapi.server.core.http;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.AbstractClientHttpRequest;
import org.springframework.http.client.reactive.AbstractClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A copy of Spring's JdkClientHttpConnector without the cache(0) it puts on the response body.
 * That operator never disconnects from its upstream, so a cancel from downstream never reaches
 * Flow.Subscription.cancel() and the socket stays open while the server keeps producing a
 * response nobody will read. Cancelling is the documented way to abort a JDK exchange (see
 * BodySubscribers.ofPublisher), and the DAS estimate stream needs it: when the browser goes
 * away, DAS only finds out when its socket closes.
 * Two things to know:
 * 1. Response cookies are not adapted. DAS sets none, and this is the one part of the original
 *    the fork drops.
 * 2. There is no request timeout. HttpRequest.Builder#timeout does not cover the body of a
 *    streamed response (JDK-8258397), so callers use a Reactor timeout instead.
 * All of this goes away once RestClient supports SSE upstream (spring-framework#35164).
 */
public class CancelPropagatingJdkConnector implements ClientHttpConnector {

    private final HttpClient httpClient;

    private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;

    public CancelPropagatingJdkConnector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
                                            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

        JdkRequest request = new JdkRequest(method, uri, bufferFactory);

        return requestCallback.apply(request).then(Mono.defer(() -> {
            HttpRequest nativeRequest = request.getNativeRequest();

            CompletableFuture<HttpResponse<Flow.Publisher<List<ByteBuffer>>>> future =
                    httpClient.sendAsync(nativeRequest, HttpResponse.BodyHandlers.ofPublisher());

            return Mono.fromCompletionStage(future)
                    .map(response -> new JdkResponse(response, bufferFactory));
        }));
    }

    /**
     * The request side, same as Spring's but with no timeout. Copied because Spring's is package
     * private, so the response fork cannot reuse it.
     */
    private static final class JdkRequest extends AbstractClientHttpRequest {

        private final HttpMethod method;
        private final URI uri;
        private final DataBufferFactory bufferFactory;
        private final HttpRequest.Builder builder;

        private JdkRequest(HttpMethod method, URI uri, DataBufferFactory bufferFactory) {
            this.method = method;
            this.uri = uri;
            this.bufferFactory = bufferFactory;
            this.builder = HttpRequest.newBuilder(uri);
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeRequest() {
            return (T) builder.build();
        }

        @Override
        protected void applyHeaders() {
            for (Map.Entry<String, List<String>> entry : getHeaders().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                    // The JDK restricts this header; the body publisher below carries the length.
                    continue;
                }
                for (String value : entry.getValue()) {
                    builder.header(entry.getKey(), value);
                }
            }
            if (!getHeaders().containsKey(HttpHeaders.ACCEPT)) {
                builder.header(HttpHeaders.ACCEPT, "*/*");
            }
        }

        @Override
        protected void applyCookies() {
            MultiValueMap<String, HttpCookie> cookies = getCookies();
            if (cookies.isEmpty()) {
                return;
            }
            builder.header(HttpHeaders.COOKIE, cookies.values().stream()
                    .flatMap(List::stream)
                    .map(HttpCookie::toString)
                    .collect(Collectors.joining(";")));
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return doCommit(() -> {
                builder.method(method.name(), toBodyPublisher(body));
                return Mono.empty();
            });
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).flatMap(Function.identity()));
        }

        @Override
        public Mono<Void> setComplete() {
            return doCommit(() -> {
                builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
                return Mono.empty();
            });
        }

        private HttpRequest.BodyPublisher toBodyPublisher(Publisher<? extends DataBuffer> body) {
            Publisher<ByteBuffer> byteBuffers = body instanceof Mono ?
                    Mono.from(body).map(JdkRequest::toByteBuffer) :
                    Flux.from(body).map(JdkRequest::toByteBuffer);

            Flow.Publisher<ByteBuffer> flow = JdkFlowAdapter.publisherToFlowPublisher(byteBuffers);
            long contentLength = getHeaders().getContentLength();

            return contentLength > 0 ?
                    HttpRequest.BodyPublishers.fromPublisher(flow, contentLength) :
                    HttpRequest.BodyPublishers.fromPublisher(flow);
        }

        private static ByteBuffer toByteBuffer(DataBuffer dataBuffer) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
            dataBuffer.toByteBuffer(byteBuffer);
            return byteBuffer;
        }
    }

    /**
     * The response side. The missing cache(0) on the body is the whole reason this file exists.
     */
    private static final class JdkResponse extends AbstractClientHttpResponse {

        private JdkResponse(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response, DataBufferFactory bufferFactory) {
            super(HttpStatusCode.valueOf(response.statusCode()),
                    adaptHeaders(response),
                    new LinkedMultiValueMap<>(),
                    adaptBody(response, bufferFactory));
        }

        private static HttpHeaders adaptHeaders(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
            Map<String, List<String>> rawHeaders = response.headers().map();
            Map<String, List<String>> map = new LinkedCaseInsensitiveMap<>(rawHeaders.size(), Locale.ROOT);
            MultiValueMap<String, String> multiValueMap = CollectionUtils.toMultiValueMap(map);
            multiValueMap.putAll(rawHeaders);
            return HttpHeaders.readOnlyHttpHeaders(multiValueMap);
        }

        private static Flux<DataBuffer> adaptBody(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response,
                                                  DataBufferFactory bufferFactory) {

            Flow.Publisher<List<ByteBuffer>> body = response.body();
            if (body == null) {
                return Flux.empty();
            }

            // No cache(0) here: a cancel from downstream has to reach the JDK subscription.
            return JdkFlowAdapter.flowPublisherToFlux(body)
                    .flatMapIterable(Function.identity())
                    .map(bufferFactory::wrap)
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        }
    }
}
