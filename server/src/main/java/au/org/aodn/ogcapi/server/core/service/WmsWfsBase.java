package au.org.aodn.ogcapi.server.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

@Slf4j
public class WmsWfsBase {

    @Autowired
    protected RestTemplate restTemplate;

    /**
     * We may get http to https response from our geoserver, so this is make sure we redirect call, although we get the url
     * from metadata, we also want to make sure the redirect is to the same server.
     * @param sourceUrl - url from metadata
     * @param response - the original that may or may not have the redirect
     * @param type - The type of response
     * @return - If it is redirect, then call the redirect location given host check, if not same response return
     * @param <T> The type of the return type
     * @throws URISyntaxException - Not expect to throw
     */
    protected <T> ResponseEntity<T> handleRedirect(String sourceUrl, ResponseEntity<T> response, Class<T> type) throws URISyntaxException {
        if(response != null && response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
            // Redirect should happen automatically but it does not so here is a safe-guard
            // the reason happens because http is use but redirect to https
            URI source = new URI(sourceUrl);
            URI redirect = response.getHeaders().getLocation();
            if(redirect.getHost().equalsIgnoreCase(source.getHost())) {
                // Only allow redirect to same server.
                return restTemplate.getForEntity(response.getHeaders().getLocation().toString(), type, Collections.emptyMap());
            }
            else {
                log.error("Redirect to different host not allowed, from {} to {}", source, redirect);
            }
        }
        return response;
    }
}
