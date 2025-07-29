package au.org.aodn.ogcapi.server.features.config;

import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class WfsServerConfig {
    // Hardcoded wfs server URLs to avoid SSRF attack and ensure only known servers are used.
    private final List<String> urls = List.of(
        "https://geoserver.imas.utas.edu.au/geoserver/wfs",
        "https://geoserver-123.aodn.org.au/geoserver/wfs",
        "https://www.cmar.csiro.au/geoserver/wfs",
        "https://geoserver.apps.aims.gov.au/aims/wfs"
    );

    public List<String> getUrls() {
        return urls;
    }

    public boolean isAllowed(String serverUrl) {
        if (serverUrl == null) {
            return false;
        }

        // Normalize the URL by removing trailing slashes and query parameters
        String normalizedUrl = serverUrl.trim();
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }

        // Remove query parameters if present
        int queryIndex = normalizedUrl.indexOf('?');
        if (queryIndex != -1) {
            normalizedUrl = normalizedUrl.substring(0, queryIndex);
        }

        return urls.contains(normalizedUrl);
    }
}
