package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsDescribeFeatureTypeResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsGetCapabilitiesResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.FeatureTypeInfo;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WFSFieldModel;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.DOWNLOADABLE_FIELDS;
import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES;
import static au.org.aodn.ogcapi.server.core.service.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static au.org.aodn.ogcapi.server.core.util.GeoserverUtils.extractLayernameOrTypenameFromUrl;
import static au.org.aodn.ogcapi.server.core.util.GeoserverUtils.roughlyMatch;

@Slf4j
public class WfsServer {
    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper;
    protected DownloadableFieldsService downloadableFieldsService;
    protected RestTemplateUtils restTemplateUtils;
    protected RestTemplate restTemplate;
    protected Search search;
    protected HttpEntity<?> pretendUserEntity;

    @Lazy
    @Autowired
    protected WfsServer self;

    public WfsServer(Search search,
                     DownloadableFieldsService downloadableFieldsService,
                     RestTemplate restTemplate,
                     RestTemplateUtils restTemplateUtils,
                     HttpEntity<?> entity) {

        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.search = search;
        this.restTemplate = restTemplate;
        this.restTemplateUtils = restTemplateUtils;
        this.downloadableFieldsService = downloadableFieldsService;
        this.pretendUserEntity = entity;
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
    public List<WFSFieldModel> getDownloadableFields(String collectionId, FeatureRequest request, String assumedWfsServer) {

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
                        ResponseEntity<String> response = restTemplateUtils.handleRedirect(
                                uri,
                                restTemplate.exchange(uri, HttpMethod.GET, pretendUserEntity, String.class),
                                String.class,
                                pretendUserEntity
                        );

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
                                    link.getAiGroup().contains(WFS_LINK_MARKER) ||
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
    public Optional<String> getFeatureServerUrlByTitle(String collectionId, String layerName) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER) && link.getTitle().equalsIgnoreCase(layerName))
                    .map(LinkModel::getHref)
                    .findFirst();
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
    public Optional<String> getFeatureServerUrlByTitleOrQueryParam(String collectionId, String layerName) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER))
                    .filter(link -> {
                        Optional<String> name = extractLayernameOrTypenameFromUrl(link.getHref());
                        return link.getTitle().equalsIgnoreCase(layerName) ||
                                (name.isPresent() && roughlyMatch(name.get(), layerName));
                    })
                    .map(LinkModel::getHref)
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    /**
     * Fetch raw feature types from WFS GetCapabilities - cached by URL.
     * This allows multiple collections sharing the same WFS server to use cached results.
     *
     * @param wfsServerUrl - The WFS server base URL
     * @return - List of all FeatureTypeInfo objects from GetCapabilities (unfiltered)
     */
    @Cacheable(value = GET_CAPABILITIES_WFS_FEATURE_TYPES)
    public List<FeatureTypeInfo> fetchCapabilitiesFeatureTypesByUrl(String wfsServerUrl) {
        try {
            // Parse the base URL to construct GetCapabilities request
            UriComponents components = UriComponentsBuilder.fromUriString(wfsServerUrl).build();

            // Build GetCapabilities URL
            UriComponentsBuilder builder = UriComponentsBuilder
                    .newInstance()
                    .scheme("https")        // hardcode to be https to avoid redirect
                    .port(components.getPort())
                    .host(components.getHost())
                    .path(components.getPath() != null ? components.getPath() : "/geoserver/ows")
                    .queryParam("service", "wfs")
                    .queryParam("request", "GetCapabilities");

            String url = builder.build().toUriString();
            log.debug("WFS GetCapabilities URL: {}", url);

            // Make the HTTPS call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, pretendUserEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse XML response
                WfsGetCapabilitiesResponse capabilitiesResponse = xmlMapper.readValue(
                        response.getBody(),
                        WfsGetCapabilitiesResponse.class
                );

                // Extract all feature types
                if (capabilitiesResponse != null
                        && capabilitiesResponse.getFeatureTypeList() != null
                        && capabilitiesResponse.getFeatureTypeList().getFeatureTypes() != null) {

                    List<FeatureTypeInfo> featureTypes = capabilitiesResponse.getFeatureTypeList().getFeatureTypes();

                    log.info("Fetched and cached WFS get-capabilities feature types: {} ", featureTypes.size());
                    return featureTypes;
                }
            }
        } catch (RestClientException | JsonProcessingException e) {
            log.error("Error fetching WFS GetCapabilities for URL: {}", wfsServerUrl, e);
            throw new RuntimeException(e);
        }

        return Collections.emptyList();
    }

    /**
     * Filter feature types based on matching with WFS links from ElasticSearch
     * Matching logic:
     * 1. Primary: link.title matches featureType.name OR featureType.title (fuzzy match)
     * 2. Fallback: extract typename from link URI, then typename matches featureType.name OR featureType.title (fuzzy match)
     *
     * @param collectionId - The uuid
     * @param featureTypes - List of feature types to filter
     * @return Filtered list of feature types that have matching WFS links
     */
    public List<FeatureTypeInfo> filterFeatureTypesByWfsLinks(String collectionId, List<FeatureTypeInfo> featureTypes) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);

        if (result.getCollections().isEmpty()) {
            log.info("Return empty feature types as no collection found for collectionId: {}", collectionId);
            return Collections.emptyList();
        }

        StacCollectionModel model = result.getCollections().get(0);

        // Filter WFS links where ai:group contains "Data Access > wfs"
        List<LinkModel> wfsLinks = model.getLinks()
                .stream()
                .filter(link -> link.getAiGroup() != null)
                .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER))
                .toList();

        // Filter feature types based on matching with WFS links
        List<FeatureTypeInfo> filteredFeatureTypes = new ArrayList<>();

        log.debug("=== Starting to match {} feature types ===", featureTypes.size());
        for (FeatureTypeInfo featureType : featureTypes) {
            boolean matched = false;

            for (LinkModel wfsLink : wfsLinks) {
                // Primary match: link.title matches featureType.name OR featureType.title
                if (wfsLink.getTitle() != null) {
                    if (roughlyMatch(wfsLink.getTitle(), featureType.getName()) ||
                            roughlyMatch(wfsLink.getTitle(), featureType.getTitle())) {
                        log.debug("  ✓ Primary match found - WFS title '{}' matches feature type '{}'",
                                wfsLink.getTitle(), featureType.getName());
                        matched = true;
                        break;
                    }
                }

                // Fallback match: extract typename from link URI
                if (wfsLink.getHref() != null) {
                    Optional<String> typename = extractLayernameOrTypenameFromUrl(wfsLink.getHref());
                    if (typename.isPresent()) {
                        if (roughlyMatch(typename.get(), featureType.getName()) ||
                                roughlyMatch(typename.get(), featureType.getTitle())) {
                            log.debug("  ✓ Fallback match found - typename '{}' matches feature type '{}'",
                                    typename.get(), featureType.getName());
                            matched = true;
                            break;
                        }
                    }
                }
            }

            if (matched) {
                filteredFeatureTypes.add(featureType);
            }
        }

        log.info("Filtered {} feature types out of {} based on WFS link matching",
                filteredFeatureTypes.size(), featureTypes.size());
        return filteredFeatureTypes;
    }

    /**
     * Get filtered feature types from WFS GetCapabilities for a specific collection.
     * First fetches all feature types (cached by URL), then filters by WFS links.
     *
     * @param collectionId - The uuid
     * @param request      - The request param (not used currently but kept for consistency)
     * @return - List of FeatureTypeInfo objects filtered by WFS link matching
     */
    public List<FeatureTypeInfo> getCapabilitiesFeatureTypes(String collectionId, FeatureRequest request) {
        Optional<List<String>> wfsServerUrls = getAllFeatureServerUrls(collectionId);

        if (wfsServerUrls.isPresent() && !wfsServerUrls.get().isEmpty()) {
            // Use the first WFS server URL
            String wfsServerUrl = wfsServerUrls.get().get(0);

            // Fetch all feature types from GetCapabilities (this call is cached by URL)
            List<FeatureTypeInfo> allFeatureTypes = self.fetchCapabilitiesFeatureTypesByUrl(wfsServerUrl);

            if (!allFeatureTypes.isEmpty()) {
                // Filter feature types based on WFS link matching
                List<FeatureTypeInfo> filteredFeatureTypes = filterFeatureTypesByWfsLinks(collectionId, allFeatureTypes);

                log.debug("Returning feature types {}", filteredFeatureTypes);
                return filteredFeatureTypes;
            }
        }

        return Collections.emptyList();
    }
}
