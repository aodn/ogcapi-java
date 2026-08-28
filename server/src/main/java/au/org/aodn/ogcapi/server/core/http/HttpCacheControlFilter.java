package au.org.aodn.ogcapi.server.core.http;

import au.org.aodn.ogcapi.server.core.configuration.OgcApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Sets {@code Cache-Control} only when HTTP cache is enabled and the request path plus
 * query parameters match a mapping exactly. Unmatched requests get no cache header.
 * <p>
 * Implemented as a filter (not a {@code HandlerInterceptor}) so the header is applied
 * before the response body is written. {@code postHandle} runs after {@code @ResponseBody}
 * conversion, by which time compression can already have committed the response.
 */
@Component
public class HttpCacheControlFilter extends OncePerRequestFilter {

    private final OgcApiProperties ogcApiProperties;

    public HttpCacheControlFilter(OgcApiProperties ogcApiProperties) {
        this.ogcApiProperties = ogcApiProperties;
    }

    @Override
    protected void doFilterInternal(
            @Nullable HttpServletRequest request,
            @Nullable HttpServletResponse response,
            @Nullable FilterChain filterChain) throws ServletException, IOException {

        OgcApiProperties.HttpCache httpCache = ogcApiProperties.httpCache();
        if (httpCache == null || !httpCache.enabled() || (request != null && !HttpMethod.GET.matches(request.getMethod()))) {
            if (filterChain != null) {
                filterChain.doFilter(request, response);
            }
            return;
        }

        if (filterChain != null) {
            CacheControlResponseWrapper wrapped = new CacheControlResponseWrapper(request, response, httpCache);
            filterChain.doFilter(request, wrapped);
            wrapped.applyIfEligible();
        }
    }

    static final class CacheControlResponseWrapper extends HttpServletResponseWrapper {

        private final HttpServletRequest request;
        private final OgcApiProperties.HttpCache httpCache;
        private boolean applied;

        CacheControlResponseWrapper(
                HttpServletRequest request,
                HttpServletResponse response,
                OgcApiProperties.HttpCache httpCache) {
            super(response);
            this.request = request;
            this.httpCache = httpCache;
        }

        void applyIfEligible() {
            if (applied) {
                return;
            }
            applied = true;
            if (getStatus() != HttpStatus.OK.value()) {
                return;
            }
            if (containsHeader(HttpHeaders.CACHE_CONTROL)) {
                return;
            }
            String path = request.getRequestURI();
            for (OgcApiProperties.Mapping mapping : httpCache.mappings()) {
                if (mapping.matches(path, request.getParameterMap())) {
                    setHeader(HttpHeaders.CACHE_CONTROL, mapping.cacheControlHeader());
                    return;
                }
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            applyIfEligible();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            applyIfEligible();
            return super.getWriter();
        }

        @Override
        public void flushBuffer() throws IOException {
            applyIfEligible();
            super.flushBuffer();
        }
    }
}
