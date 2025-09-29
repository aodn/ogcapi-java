package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.WfsDescribeFeatureTypeResponse;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.WmsWfsBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

@Slf4j
public class WfsServer extends WmsWfsBase {
    private final List<String> urls = List.of(
            "https://geoserver.imas.utas.edu.au/geoserver/wfs",
            "https://geoserver-123.aodn.org.au/geoserver/wfs",
            "https://www.cmar.csiro.au/geoserver/wfs",
            "https://geoserver.apps.aims.gov.au/aims/wfs"
    );

    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper;

    @Autowired
    protected DownloadableFieldsService downloadableFieldsService;

    @Autowired
    protected Search search;

    public WfsServer() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Cacheable(value = "downloadable-fields")
    public List<DownloadableFieldModel> getDownloadableFields(String collectionId, FeatureRequest request) {

        Optional<List<String>> mapFeatureUrl = getFeatureServerUrl(collectionId, request);

        if(mapFeatureUrl.isPresent()) {
            // Keep trying all possible url until one get response
            for(String url: mapFeatureUrl.get()) {
                try {
                    String uri = downloadableFieldsService.createFeatureFieldQueryUrl(url, request);
                    if (uri != null) {
                        log.debug("Try Url to wfs {}", uri);
                        ResponseEntity<String> response = handleRedirect(uri, restTemplate.getForEntity(uri, String.class), String.class);

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            return DownloadableFieldsService.convertWfsResponseToDownloadableFields(
                                    xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class)
                            );
                        } else {
                            throw new DownloadableFieldsNotFoundException(
                                    String.format("No downloadable fields found for call '%s'", uri)
                            );
                        }
                    }
                } catch (URISyntaxException | JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (DownloadableFieldsNotFoundException de) {
                    throw de;
                } catch (RuntimeException re) {
                    throw new DownloadableFieldsNotFoundException("No downloadable fields found due to remote connection timeout");
                }
            }
        }
        return List.of();
    }
    /**
     * Find the url that is able to get WFS call, this can be found in ai:Group or it is an ows url
     * @param collectionId - The uuid
     * @return - The wms server link.
     */
    protected Optional<List<String>> getFeatureServerUrl(String collectionId, FeatureRequest request) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if(!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return Optional.of(
                    model.getLinks()
                            .stream()
                            .filter(link -> link.getAiGroup() != null)
                            .filter(link ->
                                    // This is the pattern for wfs link
                                    link.getAiGroup().contains("Data Access > wfs") ||
                                    // The data itself can be unclean, ows is another option where it works with wfs
                                    link.getHref().contains("/ows")
                            )
                            .map(LinkModel::getHref)
                            .toList()
            );
        }
        else {
            return Optional.empty();
        }
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
