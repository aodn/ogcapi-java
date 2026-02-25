package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import au.org.aodn.ogcapi.server.core.exception.GeoserverFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.GeoserverLayersNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.*;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.util.DatetimeUtils;
import au.org.aodn.ogcapi.server.core.util.GeometryUtils;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.DOWNLOADABLE_FIELDS;
import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WFS_FEATURE_TYPES;
import static au.org.aodn.ogcapi.server.core.service.geoserver.wfs.WfsDefaultParam.WFS_LINK_MARKER;
import static au.org.aodn.ogcapi.server.core.util.GeoserverUtils.*;

@Slf4j
public class WfsServer {
    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper;
    protected RestTemplateUtils restTemplateUtils;
    protected RestTemplate restTemplate;
    protected Search search;
    protected HttpEntity<?> pretendUserEntity;
    protected WfsDefaultParam wfsDefaultParam;

    @Lazy
    @Autowired
    protected WfsServer self;

    /**
     * Internal use only to compress the number of argument pass on function call.
     */
    @Getter
    @Setter
    @SuperBuilder
    public static class WfsFeatureRequest extends FeatureRequest {
        private String server;
    }

    public WfsServer(Search search,
                     RestTemplate restTemplate,
                     RestTemplateUtils restTemplateUtils,
                     HttpEntity<?> entity,
                     WfsDefaultParam wfsDefaultParam) {

        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.search = search;
        this.restTemplate = restTemplate;
        this.restTemplateUtils = restTemplateUtils;
        this.pretendUserEntity = entity;
        this.wfsDefaultParam = wfsDefaultParam;
    }
    /**
     * Build CQL filter for temporal and spatial constraints
     */
    protected String buildCqlFilter(String serverUrl, String uuid, String layerName, String sd, String ed, Object multiPolygon) {

        WfsFields wfsFieldModel = self.getDownloadableFields(
                uuid,
                WfsServer.WfsFeatureRequest.builder()
                        .layerName(layerName)
                        .server(serverUrl)
                        .build()
        );
        log.debug("WFSFieldModel by wfs typename: {}", wfsFieldModel);

        // Validate start and end dates
        final String startDate = DatetimeUtils.validateAndFormatDate(sd, true);
        final String endDate = DatetimeUtils.validateAndFormatDate(ed, false);

        StringBuilder cqlFilter = new StringBuilder();

        if (wfsFieldModel == null || wfsFieldModel.getFields() == null) {
            return cqlFilter.toString();
        }

        List<WfsField> fields = wfsFieldModel.getFields();

        // Possible to have multiple days, better to consider all
        List<WfsField> temporalField = fields.stream()
                .filter(field -> "dateTime".equals(field.getType()) || "date".equals(field.getType()))
                .toList();

        // Add temporal filter only if both dates are specified
        if (!temporalField.isEmpty() && startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            List<String> cqls = new ArrayList<>();
            temporalField.forEach(temp ->
                    cqls.add(String.format("(%s DURING %sT00:00:00Z/%sT23:59:59Z)", temp.getName(), startDate, endDate))
            );
            cqlFilter.append("(").append(String.join(" OR ", cqls)).append(")");
        }

        // Find geometry field
        Optional<WfsField> geometryField = fields.stream()
                .filter(field -> "geometrypropertytype".equalsIgnoreCase(field.getType()))
                .findFirst();

        // Add spatial filter
        if (geometryField.isPresent() && multiPolygon != null) {
            String fieldName = geometryField.get().getName();

            String wkt = GeometryUtils.convertToWkt(multiPolygon);

            if ((wkt != null) && !cqlFilter.isEmpty()) {
                cqlFilter.append(" AND ");
            }

            if (wkt != null) {
                cqlFilter.append("INTERSECTS(")
                        .append(fieldName)
                        .append(",")
                        .append(wkt)
                        .append(")");
            }
        }

        return cqlFilter.toString();
    }
    /**
     * Build WFS GetFeature URL
     */
    protected String createWfsRequestUrl(String wfsUrl, String layerName, List<String> fields, String cqlFilter, String outputFormat) {
        UriComponents components = UriComponentsBuilder.fromUriString(wfsUrl).build();
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("https")  // Force HTTPS to fix redirect
                .host(components.getHost())
                .path(Objects.requireNonNull(components.getPath()));

        if (components.getPort() != -1) {
            builder.port(components.getPort());
        }

        Map<String, String> param = new HashMap<>(wfsDefaultParam.getDownload());
        param.put("typeName", layerName);
        param.put("outputFormat", outputFormat == null ? "text/csv" : outputFormat);

        if (fields != null) {
            param.put("propertyName", String.join(",", fields));
        }
        // Add general query parameters
        param.forEach((key, value) -> {
            if (value != null) {
                builder.queryParam(key, value);
            }
        });

        // Add CQL filter if present
        if (cqlFilter != null && !cqlFilter.isEmpty()) {
            builder.queryParam("cql_filter", cqlFilter);
        }

        return builder.build().toUriString();
    }

    /**
     * Get all WFS links from a collection.
     *
     * @param collectionId - The uuid
     * @return - List of WFS LinkModel objects from the collection
     */
    protected List<LinkModel> getWfsLinks(String collectionId) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);

        if (result.getCollections().isEmpty()) {
            log.info("No collection found for collectionId: {}", collectionId);
            return Collections.emptyList();
        }

        StacCollectionModel model = result.getCollections().get(0);

        // Filter WMS links where ai:group contains WMS_LINK_MARKER
        return model.getLinks()
                .stream()
                .filter(link -> link.getAiGroup() != null)
                .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER))
                .toList();
    }

    protected String createCapabilitiesQueryUrl(String wfsServerUrl) {
        // Parse the base URL to construct GetCapabilities request
        UriComponents components = UriComponentsBuilder.fromUriString(wfsServerUrl).build();

        // Build GetCapabilities URL
        UriComponentsBuilder builder = UriComponentsBuilder
                .newInstance()
                .scheme("https")        // hardcode to be https to avoid redirect
                .port(components.getPort())
                .host(components.getHost())
                .path(components.getPath() != null ? components.getPath() : "/geoserver/ows");

        Map<String, String> params = wfsDefaultParam.getCapabilities();
        params.forEach(builder::queryParam);

        return builder.build().toUriString();
    }

    protected String createFeatureFieldQueryUrl(String url, FeatureRequest request) {
        UriComponents components = UriComponentsBuilder.fromUriString(url).build();
        if (components.getPath() != null) {
            // Now depends on the service, we need to have different arguments
            List<String> pathSegments = components.getPathSegments();
            if (!pathSegments.isEmpty()) {
                Map<String, String> param = new HashMap<>(wfsDefaultParam.getFields());

                // Now we add the missing argument from the request
                param.put("TYPENAME", request.getLayerName());

                // This is the normal route
                UriComponentsBuilder builder = UriComponentsBuilder
                        .newInstance()
                        .scheme("https")
                        .port(components.getPort())
                        .host(components.getHost())
                        .path(components.getPath());

                param.forEach((key, value) -> {
                    if (value != null) {
                        builder.queryParam(key, value);
                    }
                });
                String target = builder.build().toUriString();
                log.debug("Url query support field in wfs {}", target);

                return target;
            }
        }
        return null;
    }

    protected String createFeatureValueQueryUrl(String url, FeatureRequest request) {
        UriComponents components = UriComponentsBuilder.fromUriString(url).build();
        if (components.getPath() != null) {
            // Now depends on the service, we need to have different arguments
            List<String> pathSegments = components.getPathSegments();
            if (!pathSegments.isEmpty()) {
                Map<String, String> param = new HashMap<>(wfsDefaultParam.getDownload());

                // Now we add the missing argument from the request
                param.put("TYPENAME", request.getLayerName());
                param.put("outputFormat", "application/json");

                if (request.getProperties() != null && !request.getProperties().contains(FeatureRequest.PropertyName.wildcard)) {
                    param.put("propertyName", String.join(
                            ",",
                            request.getProperties().stream().map(Enum::name).toList())
                    );
                    param.put("sortBy", String.join(
                            ",",
                            // Assume always sort by desc
                            request.getProperties().stream().map(p -> String.format("%s+D", p.name())).toList())
                    );
                }

                // This is the normal route
                UriComponentsBuilder builder = UriComponentsBuilder
                        .newInstance()
                        .scheme("https")
                        .port(components.getPort())
                        .host(components.getHost())
                        .path(components.getPath());

                param.forEach((key, value) -> {
                    if (value != null) {
                        builder.queryParam(key, value);
                    }
                });
                String target = builder.build().toUriString();
                log.debug("Url query field value in wfs {}", target);

                return target;
            }
        }
        return null;
    }

    /**
     * Convert WFS response to WFSFieldModel.
     * The typename is extracted from the top-level xsd:element (e.g., <xsd:element name="aatams_sattag_dm_profile_map" .../>)
     */
    protected static WfsFields convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
        String typename = null;
        if (wfsResponse.getTopLevelElements() != null && !wfsResponse.getTopLevelElements().isEmpty()) {
            typename = wfsResponse.getTopLevelElements().get(0).getName();
        }

        List<WfsField> fields = wfsResponse.getComplexTypes() != null ?
                wfsResponse.getComplexTypes().stream()
                        .filter(complexType -> complexType.getComplexContent() != null)
                        .filter(complexType -> complexType.getComplexContent().getExtension() != null)
                        .filter(complexType -> complexType.getComplexContent().getExtension().getSequence() != null)
                        .flatMap(complexType -> {
                            List<WfsDescribeFeatureTypeResponse.Element> elements =
                                    complexType.getComplexContent().getExtension().getSequence().getElements();
                            return elements != null ? elements.stream() : Stream.empty();
                        })
                        .filter(element -> element.getName() != null && element.getType() != null)
                        .map(element -> WfsField.builder()
                                .label(element.getName())
                                .name(element.getName())
                                // The type can be in format of "xsd:date", we only want the actual type name "date"
                                .type(element.getType().contains(":") ? element.getType().split(":")[1] : element.getType())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();

        return WfsFields.builder()
                .typename(typename)
                .fields(fields)
                .build();
    }

    public <T> T getFieldValues(String collectionId, WfsFeatureRequest request, ParameterizedTypeReference<T> tClass) {
        Optional<List<String>> mapFeatureUrl = request.getServer() != null ?
                Optional.of(List.of(request.getServer())) :
                getAllFeatureServerUrls(collectionId);

        if (mapFeatureUrl.isPresent()) {
            // Keep trying all possible url until one get response
            for (String url : mapFeatureUrl.get()) {
                String uri = createFeatureValueQueryUrl(url, request);
                try {
                    if (uri != null) {
                        ResponseEntity<T> response =
                                restTemplate.exchange(uri, HttpMethod.GET, pretendUserEntity, tClass
                                );

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            return response.getBody();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Ignore error for {}, will try another url", uri);
                }
            }
        }
        return null;
    }

    /**
     * Get the downloadable fields for a given collection id and layer name
     *
     * @param collectionId - The uuid of the collection
     * @param request      - The feature request containing the layer name
     * @return - WFSFieldModel containing typename and fields
     */
    @Cacheable(value = DOWNLOADABLE_FIELDS)
    public WfsFields getDownloadableFields(String collectionId, WfsFeatureRequest request) {

        Optional<List<String>> mapFeatureUrl = request.getServer() != null ?
                Optional.of(List.of(request.getServer())) :
                getAllFeatureServerUrls(collectionId);

        if (mapFeatureUrl.isPresent()) {
            // Keep trying all possible url until one get response
            for (String url : mapFeatureUrl.get()) {
                String uri = createFeatureFieldQueryUrl(url, request);
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
                            return convertWfsResponseToDownloadableFields(
                                    xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class)
                            );
                        }
                    }
                } catch (URISyntaxException | JsonProcessingException | RestClientException e) {
                    log.debug("Ignore error for {}, will try another url", uri);
                }
            }
        } else {
            return null;
        }
        throw new GeoserverFieldsNotFoundException("No downloadable fields found for all url");
    }

    public List<WfsFields> getWFSFields(String collectionId, WfsServer.WfsFeatureRequest request) {
        List<WfsFields> wfsFields = new ArrayList<>();

        // If typename is provided, use it directly
        // If no typename provided, get fields for all layers from collection WFS links
        if (request.getLayerName() != null && !request.getLayerName().isEmpty()) {
            wfsFields.add(self.getDownloadableFields(collectionId, request));
        } else {
            log.debug("No layer name provided in request, get fields for all WFS links");
            List<String> typeNamesToProcess = new ArrayList<>();

            // Get all wfs links and extract typename from the link
            List<LinkModel> wfsLinks = this.getWfsLinks(collectionId);
            for (LinkModel wfsLink : wfsLinks) {
                extractLayernameOrTypenameFromLink(wfsLink).ifPresent(typeNamesToProcess::add);
            }
            // fetch downloadable fields for each typename
            for (String typeName : typeNamesToProcess) {
                WfsServer.WfsFeatureRequest requestModified = WfsServer.WfsFeatureRequest.builder()
                        .layerName(typeName)
                        .build();

                try {
                    WfsFields fields = self.getDownloadableFields(collectionId, requestModified);
                    if (fields != null) {
                        wfsFields.add(fields);
                    }
                } catch (GeoserverFieldsNotFoundException e) {
                    log.debug("No fields found for typename {}, continue with other typename", typeName);
                }
            }
        }

        if (wfsFields.isEmpty()) {
            throw new GeoserverFieldsNotFoundException("No downloadable fields found for uuid " + collectionId);
        }

        return wfsFields;
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
    public Optional<String> getFeatureServerUrlByTitleOrQueryParam(String collectionId, String layerName) {
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            log.info("start to find wfs link for collectionId {} with layerName {}, total links to check {}", collectionId, layerName, model.getLinks().size());
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains(WFS_LINK_MARKER))
                    .filter(link -> {
                        Optional<String> name = extractLayernameOrTypenameFromUrl(link.getHref());
                        return roughlyMatch(link.getTitle(), layerName) ||
                                (name.isPresent() && roughlyMatch(name.get(), layerName));
                    })
                    .map(LinkModel::getHref)
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }


    /**
     * Find the WFS server URL for a given collection and layer name.
     * First tries to match by title or query param, then falls back to the first available WFS link.
     *
     * @param collectionId - The uuid
     * @param layerName    - The layer name to match the title
     * @return - The matched wfs server link, or the first available one if no match found
     */
    public Optional<String> getFeatureServerUrl(String collectionId, String layerName) {
        Optional<String> url = getFeatureServerUrlByTitleOrQueryParam(collectionId, layerName);
        if (url.isPresent()) {
            log.debug("Found WFS link by title/query param for collectionId {} with layerName {}: {}", collectionId, layerName, url.get());
            return url;
        }

        log.debug("No WFS link matched by title/query param for collectionId {} with layerName {}, falling back to first available WFS link", collectionId, layerName);
        Optional<List<String>> allUrls = getAllFeatureServerUrls(collectionId);
        return allUrls.filter(list -> !list.isEmpty()).map(list -> list.get(0));
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
            String url = createCapabilitiesQueryUrl(wfsServerUrl);
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

                if (filteredFeatureTypes.isEmpty()) {
                    throw new GeoserverLayersNotFoundException("No WFS layer is found for uuid " + collectionId);
                }

                log.debug("Returning feature types {}", filteredFeatureTypes);
                return filteredFeatureTypes;
            }
        }

        throw new GeoserverLayersNotFoundException("No valid WFS server url is found for uuid " + collectionId);
    }
}
