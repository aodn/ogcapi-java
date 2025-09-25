package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.wfs.WfsInfo;
import au.org.aodn.ogcapi.server.core.service.ElasticSearch;
import au.org.aodn.ogcapi.server.core.service.Search;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
public class WfsServer {
    @Autowired
    protected Search elasticSearch;

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

    /**
     * Extract WFS URL and type name from collection links given uuid
     */
    public WfsInfo getWfsInfo(String uuid, String typeName) {
        // Get collection from ElasticSearch
        ElasticSearch.SearchResult<StacCollectionModel> searchResult =
                elasticSearch.searchCollections(List.of(uuid), null);

        if (searchResult.getCollections().isEmpty()) {
            log.warn("Collection with UUID {} not found", uuid);
            return null;
        }

        StacCollectionModel collection = searchResult.getCollections().get(0);
        if (collection.getLinks() == null) {
            log.warn("Collection with UUID {} has no links", uuid);
            return null;
        }

        // Find WFS link with matching layer name (title)
        Optional<LinkModel> wfsLink = collection.getLinks().stream()
                .filter(link -> link.getAiGroup() != null && link.getAiGroup().contains("wfs"))
                .filter(link -> typeName.equals(link.getTitle()))
                .findFirst();

        if (wfsLink.isEmpty()) {
            log.warn("No WFS link found with UUID {} and layer name: {}", uuid, typeName);
            return null;
        }

        String href = wfsLink.get().getHref();
        String title = wfsLink.get().getTitle();

        if (href == null || title == null) {
            log.warn("No valid WFS link found found with UUID {} and layer name: {}", uuid, typeName);
            return null;
        }

        // The href is the WFS server URL, title is the layer name
        return new WfsInfo(href, title);
    }
}
