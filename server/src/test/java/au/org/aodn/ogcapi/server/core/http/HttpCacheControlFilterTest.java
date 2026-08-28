package au.org.aodn.ogcapi.server.core.http;

import au.org.aodn.ogcapi.server.core.configuration.OgcApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpCacheControlFilterTest {

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

    private static void run(HttpCacheControlFilter filter, MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        filter.doFilter(request, response, chain);
    }

    private static FilterChain statusThenWrite(int status) {
        return (request, response) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setStatus(status);
            http.getOutputStream().write(new byte[2048]);
        };
    }

    @Test
    void noHeaderWhenDisabled() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, matchingGet(), response, statusThenWrite(200));

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void setsHeaderOnExactMatchBeforeBodyWrite() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, matchingGet(), response, statusThenWrite(200));

        assertEquals("public, max-age=3600", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noCacheWhenPathDiffers() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setRequestURI("/api/v1/ogc/collections/other");
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, request, response, statusThenWrite(200));

        assertEquals(HttpCacheControlFilter.NO_CACHE, response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noCacheWhenExtraQueryParamPresent() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setParameter("q", "fish");
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, request, response, statusThenWrite(200));

        assertEquals(HttpCacheControlFilter.NO_CACHE, response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noCacheWhenExpectedParamValueDiffers() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletRequest request = matchingGet();
        request.setParameter("sortby", "-score");
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, request, response, statusThenWrite(200));

        assertEquals(HttpCacheControlFilter.NO_CACHE, response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void noCacheWhenNotOk() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, matchingGet(), response, statusThenWrite(500));

        assertEquals(HttpCacheControlFilter.NO_CACHE, response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void doesNotOverrideExistingCacheControl() throws Exception {
        HttpCacheControlFilter filter = new HttpCacheControlFilter(properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, matchingGet(), response, (request, resp) -> {
            HttpServletResponse http = (HttpServletResponse) resp;
            http.setStatus(200);
            http.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            http.getOutputStream().write(new byte[2048]);
        });

        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }
}
