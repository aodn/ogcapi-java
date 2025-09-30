package au.org.aodn.ogcapi.server.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RestTemplateUtils {

    protected RestTemplate restTemplate;

    public RestTemplateUtils(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
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
    public <T> ResponseEntity<T> handleRedirect(String sourceUrl, ResponseEntity<T> response, Class<T> type) throws URISyntaxException {
        // Redirect should happen automatically but it does not so here is a safe-guard
        // the reason happens because http is use but redirect to https
        if(response != null && response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
            // The reason we do this because we must use the un-encode param, once encoded especially with CQL_FILTER will
            // fail on geoserver
            String redirect = String.format("%s://%s%s%s",
                    response.getHeaders().getLocation().getScheme(),
                    response.getHeaders().getLocation().getHost(),
                    response.getHeaders().getLocation().getPath(),
                    response.getHeaders().getLocation().getRawQuery() != null ?
                            "?" + response.getHeaders().getLocation().getRawQuery().replace("%20", " ") :
                            ""
            );
            if(haveSameHost(sourceUrl, redirect)) {
                // Only allow redirect to same server.
                log.info("Redirect from {} to {}", sourceUrl , redirect);
                return restTemplate.getForEntity(redirect, type, Collections.emptyMap());
            }
            else {
                log.error("Redirect to different host not allowed, from {} to {}", sourceUrl , redirect);
            }
        }
        return response;
    }

    protected static boolean haveSameHost(String url1, String url2) {
        // Regex to match the host part of a URL
        String hostRegex = "(?:https?://)?((?:[a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+\\.[a-zA-Z]{2,})(?::\\d+)?(?:/|$|\\?)";
        Pattern pattern = Pattern.compile(hostRegex, Pattern.CASE_INSENSITIVE);

        // Extract host from first URL
        Matcher matcher1 = pattern.matcher(url1);
        String host1 = null;
        if (matcher1.find()) {
            host1 = matcher1.group(1);
        }

        // Extract host from second URL
        Matcher matcher2 = pattern.matcher(url2);
        String host2 = null;
        if (matcher2.find()) {
            host2 = matcher2.group(1);
        }

        // Compare hosts (case-insensitive)
        return host1 != null && host1.equalsIgnoreCase(host2);
    }
}
