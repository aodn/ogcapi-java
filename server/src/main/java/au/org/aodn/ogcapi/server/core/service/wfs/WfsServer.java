package au.org.aodn.ogcapi.server.core.service.wfs;

import java.net.URI;
import java.util.List;

public class WfsServer {
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
        return findMatchingUrl(serverUrl) != null;
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
     * Find matching URL from whitelist based on host only
     * Example: "<a href="http://geoserver.imas.utas.edu.au/geoserver/ows">ows</a>"
     * matches "<a href="https://geoserver.imas.utas.edu.au/geoserver/wfs">wfs</a>"
     * The matching is based on host only, ignoring protocol and path
     */
    private String findMatchingUrl(String userProvidedUrl) {
        if (userProvidedUrl == null) {
            return null;
        }

        try {
            URI userUri = URI.create(normalizeUrl(userProvidedUrl));
            String userHost = userUri.getHost();

            if (userHost == null) {
                return null;
            }

            return urls.stream()
                    .filter(approvedUrl -> {
                        try {
                            URI approvedUri = URI.create(approvedUrl);
                            String approvedHost = approvedUri.getHost();
                            return userHost.equals(approvedHost);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * SSRF Protection: Validate user input against whitelist and return approved URL
     * This ensures no user input is directly used in HTTP requests
     */
    public String validateAndGetApprovedServerUrl(String userProvidedUrl) {
        String matchedUrl = findMatchingUrl(userProvidedUrl);

        if (matchedUrl == null) {
            throw new au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException(
                    String.format("Access to WFS server '%s' is not authorized. Only approved servers are allowed.", userProvidedUrl)
            );
        }

        // Return the exact approved URL from our whitelist, not user input
        return matchedUrl;
    }
}
