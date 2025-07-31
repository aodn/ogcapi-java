package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class WfsServerConfig {
    private final List<String> urls = List.of(
        "https://geoserver.imas.utas.edu.au/geoserver/wfs",
        "https://geoserver-123.aodn.org.au/geoserver/wfs",
        "https://www.cmar.csiro.au/geoserver/wfs",
        "https://geoserver.apps.aims.gov.au/aims/wfs"
    );

    public boolean isAllowed(String serverUrl) {
        if (serverUrl == null) {
            return false;
        }
        return urls.contains(normalizeUrl(serverUrl));
    }

    public String normalizeUrl(String serverUrl) {
        if (serverUrl == null) {
            return null;
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

        return normalizedUrl;
    }

    /**
     * SSRF Protection: Validate user input against whitelist and return approved URL
     * This ensures no user input is directly used in HTTP requests
     */
    public String validateAndGetApprovedServerUrl(String userProvidedUrl) {
        if (!isAllowed(userProvidedUrl)) {
            throw new au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException(
                String.format("Access to WFS server '%s' is not authorized. Only approved servers are allowed.", userProvidedUrl)
            );
        }

        // Return the exact approved URL from our whitelist, not user input
        return urls.stream()
            .filter(approvedUrl -> normalizeUrl(userProvidedUrl).equals(normalizeUrl(approvedUrl)))
            .findFirst()
            .orElseThrow(() -> new au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException(
                String.format("Access to WFS server '%s' is not authorized. Only approved servers are allowed.", userProvidedUrl)
            ));
    }
}
