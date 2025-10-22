package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsDescribeFeatureTypeResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.DOWNLOADABLE_FIELDS;

@Slf4j
public class WfsServer {
    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper;

    @Autowired
    protected DownloadableFieldsService downloadableFieldsService;

    @Autowired
    protected RestTemplateUtils restTemplateUtils;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected Search search;

    public WfsServer() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Get the downloadable fields for a given collection id and layer name
     *
     * @param collectionId     - The uuid of the collection
     * @param request          - The feature request containing the layer name
     * @param assumedWfsServer - An optional wfs server url to use instead of searching for one
     * @return - A list of downloadable fields
     */
    @Cacheable(value = DOWNLOADABLE_FIELDS)
    public List<DownloadableFieldModel> getDownloadableFields(String collectionId, FeatureRequest request, String assumedWfsServer) {

        Optional<List<String>> mapFeatureUrl = assumedWfsServer != null ?
                Optional.of(List.of(assumedWfsServer)) :
                getAllFeatureServerUrls(collectionId);

        if (mapFeatureUrl.isPresent()) {
            // Keep trying all possible url until one get response
            for (String url : mapFeatureUrl.get()) {
                String uri = downloadableFieldsService.createFeatureFieldQueryUrl(url, request);
                try {
                    if (uri != null) {
                        log.debug("Try Url to wfs {}", uri);
                        ResponseEntity<String> response = restTemplateUtils.handleRedirect(uri, restTemplate.getForEntity(uri, String.class), String.class);

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            return DownloadableFieldsService.convertWfsResponseToDownloadableFields(
                                    xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class)
                            );
                        }
                    }
                } catch (URISyntaxException | JsonProcessingException | RestClientException e) {
                    log.debug("Ignore error for {}, will try another url", uri);
                }
            }
        } else {
            return List.of();
        }
        throw new DownloadableFieldsNotFoundException("No downloadable fields found for all url");
    }

    /**
     * Find the url that is able to get WFS call, this can be found in ai:Group or it is an ows url
     *
     * @param collectionId - The uuid
     * @return - All the possible wfs server links
     */
    protected Optional<List<String>> getAllFeatureServerUrls(String collectionId) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
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
        } else {
            return Optional.empty();
        }
    }

    /**
     * Find the url that is able to get WFS call, this can be found in ai:Group
     *
     * @param collectionId - The uuid
     * @param layerName    - The layer name to match the title
     * @return - The first wfs server link if found
     */
    protected Optional<String> getFeatureServerUrl(String collectionId, String layerName) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains("Data Access > wfs") && link.getTitle().equalsIgnoreCase(layerName))
                    .map(LinkModel::getHref)
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    /**
     * Fuzzy match utility to compare layer names, ignoring namespace prefixes
     * For example: "underway:nuyina_underway_202122020" matches "nuyina_underway_202122020"
     *
     * @param text1 - First text to compare
     * @param text2 - Second text to compare
     * @return true if texts match (after removing namespace prefix)
     */
    protected boolean fuzzyMatch(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return false;
        }

        // Remove namespace prefix (text before ":")
        String normalized1 = text1.contains(":") ? text1.substring(text1.indexOf(":") + 1) : text1;
        String normalized2 = text2.contains(":") ? text2.substring(text2.indexOf(":") + 1) : text2;

        return normalized1.equalsIgnoreCase(normalized2);
    }

    /**
     * Extract typename from WFS URL query parameters
     *
     * @param url - The WFS URL
     * @return typename if found, empty otherwise
     */
    protected Optional<String> extractTypenameFromUrl(String url) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            var queryParams = builder.build().getQueryParams();

            // Try different parameter name variations
            List<String> typeNames = queryParams.get("typeName");
            if (typeNames == null || typeNames.isEmpty()) {
                typeNames = queryParams.get("TYPENAME");
            }
            if (typeNames == null || typeNames.isEmpty()) {
                typeNames = queryParams.get("typename");
            }
            if (typeNames != null && !typeNames.isEmpty()) {
                // URL decode the typename (e.g., "underway%3Aunderway_60" -> "underway:underway_60")
                String typename = UriUtils.decode(typeNames.get(0), StandardCharsets.UTF_8);
                return Optional.of(typename);
            }
        } catch (Exception e) {
            log.debug("Failed to extract typename from URL: {}", url, e);
        }
        return Optional.empty();
    }

    /**
     * Filter WMS layers based on matching with WFS links
     * Matching logic:
     * 1. Primary: link.title matches layer.name OR layer.title (fuzzy match)
     * 2. Fallback: extract typename from link URI, then typename matches layer.name OR layer.title (fuzzy match)
     *
     * @param collectionId - The uuid
     * @param layers       - List of layers to filter
     * @return Filtered list of WMS layers that have matching WFS links
     */
    public List<LayerInfo> filterLayersByWfsLinks(String collectionId, List<LayerInfo> layers) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);

        if (result.getCollections().isEmpty()) {
            log.info("Return all layers if as no collection found for collectionId: {}", collectionId);
            return layers;
        }

        StacCollectionModel model = result.getCollections().get(0);

        // Filter WFS links where ai:group == "Data Access > wfs"
        List<LinkModel> wfsLinks = model.getLinks()
                .stream()
                .filter(link -> link.getAiGroup() != null)
                .filter(link -> link.getAiGroup().contains("Data Access > wfs"))
                .toList();

        if (wfsLinks.isEmpty()) {
            log.warn("Return all layers if as no WFS links found for collection {}", collectionId);
            return layers;
        }

        // Filter WMS layers based on matching with WFS links
        List<LayerInfo> filteredLayers = new ArrayList<>();

        log.debug("=== Starting to match {} layers ===", layers.size());
        for (LayerInfo layer : layers) {
            boolean matched = false;

            for (LinkModel wfsLink : wfsLinks) {
                // Primary match: link.title matches layer.name OR layer.title
                if (wfsLink.getTitle() != null) {
                    if (fuzzyMatch(wfsLink.getTitle(), layer.getName()) ||
                            fuzzyMatch(wfsLink.getTitle(), layer.getTitle())) {
                        log.debug("  ✓ Primary match found - WFS title '{}' matches layer '{}'",
                                wfsLink.getTitle(), layer.getName());
                        matched = true;
                        break;
                    }
                }

                // Fallback match: extract typename from link URI
                if (!matched && wfsLink.getHref() != null) {
                    Optional<String> typename = extractTypenameFromUrl(wfsLink.getHref());
                    if (typename.isPresent()) {
                        if (fuzzyMatch(typename.get(), layer.getName()) ||
                                fuzzyMatch(typename.get(), layer.getTitle())) {
                            log.debug("  ✓ Fallback match found - typename '{}' matches layer '{}'",
                                    typename.get(), layer.getName());
                            matched = true;
                            break;
                        }
                    }
                }
            }

            if (matched) {
                filteredLayers.add(layer);
            }
        }

        log.info("Filtered {} layers out of {} based on WFS link matching",
                filteredLayers.size(), layers.size());
        return filteredLayers;
    }
}
