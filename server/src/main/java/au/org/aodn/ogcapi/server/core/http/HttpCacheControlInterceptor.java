package au.org.aodn.ogcapi.server.core.http;

import au.org.aodn.ogcapi.server.core.configuration.OgcApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sets {@code Cache-Control} only when HTTP cache is enabled and the request path plus
 * query parameters match a mapping exactly. Unmatched requests get no cache header.
 */
@Component
public class HttpCacheControlInterceptor implements HandlerInterceptor {

    private final OgcApiProperties ogcApiProperties;

    public HttpCacheControlInterceptor(OgcApiProperties ogcApiProperties) {
        this.ogcApiProperties = ogcApiProperties;
    }

    @Override
    public void postHandle(
            @Nullable HttpServletRequest request,
            @Nullable HttpServletResponse response,
            @Nullable Object handler,
            org.springframework.web.servlet.ModelAndView modelAndView) {

        OgcApiProperties.HttpCache httpCache = ogcApiProperties.httpCache();
        if (httpCache == null || !httpCache.enabled()) {
            return;
        }
        if (request != null && !HttpMethod.GET.matches(request.getMethod())) {
            return;
        }
        if (response != null && response.getStatus() != HttpStatus.OK.value()) {
            return;
        }
        if (response != null && response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            return;
        }

        if (request != null && response != null) {
            String path = request.getRequestURI();
            for (OgcApiProperties.Mapping mapping : httpCache.mappings()) {
                if (mapping.matches(path, request.getParameterMap())) {
                    response.setHeader(HttpHeaders.CACHE_CONTROL, mapping.cacheControlHeader());
                    return;
                }
            }

        }
    }
}
