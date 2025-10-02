package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsDescribeFeatureTypeResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
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

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

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
    @Cacheable(value = "downloadable-fields")
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
}
