package au.org.aodn.ogcapi.server.core.http;

import au.org.aodn.ogcapi.server.core.configuration.OgcApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpCacheControlInterceptorTest {

    private static final String PATH = "/api/v1/ogc/collections";
    private static final Map<String, String> EXPECTED = Map.of(
            "properties", "id,temporal",
            "filter", "temporal after 1970-01-01T00:00:00Z",
            "sortby", "id"
    );

    private static OgcApiProperties properties(boolean enabled) {
        return new OgcApiProperties(
                new OgcApiProperties.Debug(false),
                new OgcApiProperties.HttpCache(
                        enabled,
                        List.of(new OgcApiProperties.Mapping(PATH, 1, EXPECTED))
                )
        );
    }

    private static MockHttpServletRequest matchingGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setParameter("properties", "id,temporal");
        request.setParameter("filter", "temporal after 1970-01-01T00:00:00Z");
        request.setParameter("sortby", "id");
        return request;
    }

    private static void postHandle(HttpCacheControlInterceptor interceptor, MockHttpServletRequest request, MockHttpServletResponse response) {
        interceptor.postHandle(request, response, new Object(), null);
    }

    @Test
    void noHeaderWhenDisabled() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(false));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        postHandle(interceptor, matchingGet(), response);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void setsHeaderOnExactMatch() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        postHandle(interceptor, matchingGet(), response);

        assertEquals("public, max-age=3600", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noHeaderWhenPathDiffers() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setRequestURI("/api/v1/ogc/collections/other");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        postHandle(interceptor, request, response);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noHeaderWhenExtraQueryParamPresent() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setParameter("q", "fish");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        postHandle(interceptor, request, response);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noHeaderWhenExpectedParamValueDiffers() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setParameter("sortby", "-score");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        postHandle(interceptor, request, response);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noHeaderWhenNotOk() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        postHandle(interceptor, matchingGet(), response);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void doesNotOverrideExistingCacheControl() {
        HttpCacheControlInterceptor interceptor = new HttpCacheControlInterceptor(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        postHandle(interceptor, matchingGet(), response);

        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }
}
