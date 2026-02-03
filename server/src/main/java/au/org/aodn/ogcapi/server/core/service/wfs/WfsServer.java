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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.DOWNLOADABLE_FIELDS;
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
            log.info("Return empty layers if as no collection found for collectionId: {}", collectionId);
            return Collections.emptyList();
        }

        StacCollectionModel model = result.getCollections().get(0);

        // Filter WFS links where ai:group == "Data Access > wfs"
        List<LinkModel> wfsLinks = model.getLinks()
                .stream()
                .filter(link -> link.getAiGroup() != null)
                .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER))
                .toList();

        // Filter WMS layers based on matching with WFS links
        List<LayerInfo> filteredLayers = new ArrayList<>();

        log.debug("=== Starting to match {} layers ===", layers.size());
        for (LayerInfo layer : layers) {
            boolean matched = false;

            for (LinkModel wfsLink : wfsLinks) {
                // Primary match: link.title matches layer.name OR layer.title
                if (wfsLink.getTitle() != null) {
                    if (roughlyMatch(wfsLink.getTitle(), layer.getName()) ||
                            roughlyMatch(wfsLink.getTitle(), layer.getTitle())) {
                        log.debug("  ✓ Primary match found - WFS title '{}' matches layer '{}'",
                                wfsLink.getTitle(), layer.getName());
                        matched = true;
                        break;  // This will skip the next if block
                    }
                }

                // Fallback match: extract typename from link URI
                if (wfsLink.getHref() != null) {
                    Optional<String> typename = extractLayernameOrTypenameFromUrl(wfsLink.getHref());
                    if (typename.isPresent()) {
                        if (roughlyMatch(typename.get(), layer.getName()) ||
                                roughlyMatch(typename.get(), layer.getTitle())) {
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

//        // Very specific logic for AODN, we favor any layer name ends with _aodn_map, so we display
//        // map layer similar to old portal, if we cannot find any then display what we have
//        List<LayerInfo> aodn_map = filteredLayers.stream().filter(l ->
//                l.getName().endsWith("_aodn_map") || l.getTitle().endsWith("_aodn_map")
//        ).toList();
//        if (!aodn_map.isEmpty()) {
//            filteredLayers = aodn_map;
//        }

        log.info("Filtered {} layers out of {} based on WFS link matching",
                filteredLayers.size(), layers.size());
        return filteredLayers;
    }
}
