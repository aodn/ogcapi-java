package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.DownloadWfsDataService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsDefaultParam;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.geoserver.wms.WmsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

@Configuration
public class GeoServerConfig {

    /**
     * Some wfs request contains non standard minetype which is not allow by the default rest template
     */
    static class WfsCustomResponseWrapper implements ClientHttpResponse {
        private final ClientHttpResponse delegate;

        public WfsCustomResponseWrapper(ClientHttpResponse delegate) {
            this.delegate = delegate;
        }

        @Override
        @Nonnull
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        @Nonnull
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        @Nonnull
        public InputStream getBody() throws IOException {
            return delegate.getBody();
        }

        @Override
        @Nonnull
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(delegate.getHeaders());
            String ct = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            if (ct != null && ct.contains("subtype=")) {
                headers.set(HttpHeaders.CONTENT_TYPE, "text/xml");
            }
            return headers;
        }
    }

    @ConditionalOnMissingBean(name = "pretendUserEntity")
    @Bean("pretendUserEntity")
    public HttpEntity<?> createPretendUserEntity() {
        // Some server do not allow program to scrap the content, so we need to pretend to be a client
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        return new HttpEntity<>(headers);
    }

    @Bean
    public WfsServer createWfsServer(Search search,
                                     RestTemplate restTemplate,
                                     RestTemplateUtils restTemplateUtils,
                                     @Qualifier("pretendUserEntity") HttpEntity<?> entity,
                                     WfsDefaultParam wfsDefaultParam) {
        return new WfsServer(search, restTemplate, restTemplateUtils, entity, wfsDefaultParam);
    }

    @Bean
    public WmsServer createWmsServer(Search search, @Lazy WfsServer wfsServer, @Qualifier("pretendUserEntity") HttpEntity<?> entity) {
        return new WmsServer(search, wfsServer, entity);
    }

    @Bean
    @ConditionalOnMissingBean(DownloadWfsDataService.class)
    public DownloadWfsDataService createDownloadWfsDataService(WfsServer wfsServer,
                                                               RestTemplate restTemplate,
                                                               @Qualifier("pretendUserEntity") HttpEntity<?> pretendUserEntity,
                                                               @Value("${app.sse.chunkSize:16384}") int chunkSize) {

        RestTemplate clone = new RestTemplate(restTemplate.getRequestFactory());
        clone.setInterceptors(new ArrayList<>(restTemplate.getInterceptors()));
        clone.getInterceptors().add((request, body, execution) -> {
            ClientHttpResponse resp = execution.execute(request, body);
            return new WfsCustomResponseWrapper(resp);
        });

        clone.setMessageConverters(new ArrayList<>(restTemplate.getMessageConverters()));
        clone.setErrorHandler(restTemplate.getErrorHandler());

        return new DownloadWfsDataService(wfsServer, clone, pretendUserEntity, chunkSize);
    }
}
