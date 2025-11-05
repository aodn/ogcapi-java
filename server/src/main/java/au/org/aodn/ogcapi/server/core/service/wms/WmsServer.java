package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.DescribeLayerResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.GetCapabilitiesResponse;
import au.org.aodn.ogcapi.server.core.model.ogc.wms.LayerInfo;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.util.RestTemplateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static au.org.aodn.ogcapi.server.core.configuration.CacheConfig.CACHE_WMS_MAP_TILE;

@Slf4j
public class WmsServer {
    protected final XmlMapper xmlMapper;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected RestTemplateUtils restTemplateUtils;

    @Lazy
    @Autowired
    protected WfsServer wfsServer;

    @Autowired
    protected Search search;

    @Autowired
    protected WmsDefaultParam wmsDefaultParam;

    @Lazy
    @Autowired
    protected WmsServer self;

    public WmsServer() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    protected String createCQLFilter(String uuid, FeatureRequest request) {
        if (request.getDatetime() != null) {
            // Special handle for date time field, the field name will be diff across dataset. So we need
            // to look it up
            String cql = "";
            try {
                Optional<String> wfsUrl = wfsServer.getFeatureServerUrlByTitleOrQueryParam(uuid, request.getLayerName());
                if(wfsUrl.isPresent()) {
                    UriComponents wfsUrlComponents = UriComponentsBuilder.fromUriString(wfsUrl.get()).build();
                    // Extract the CQL if existing in the WFS, we need to apply it to the WMS as well
                    if(wfsUrlComponents.getQueryParams().get("cql_filter") != null) {
                        cql = wfsUrlComponents.getQueryParams().get("cql_filter").get(0) + " AND ";
                    }
                    else if(wfsUrlComponents.getQueryParams().get("CQL_FILTER") != null) {
                        cql = wfsUrlComponents.getQueryParams().get("CQL_FILTER").get(0) + " AND ";
                    }
                }

                List<DownloadableFieldModel> m = this.getDownloadableFields(uuid, request);
                List<DownloadableFieldModel> target = m.stream()
                        .filter(value -> "dateTime".equalsIgnoreCase(value.getType()))
                        .toList();

                if (!target.isEmpty()) {

                    if (target.size() > 2) {
                        // Try to find possible fields where it contains start end min max
                        target = target.stream()
                                .filter(v -> Stream.of("start", "end", "min", "max").anyMatch(k -> v.getName().contains(k)))
                                .toList();
                    }

                    if (target.size() == 2) {
                        // Due to no standard name, we try our best to guess if 2 dateTime field
                        String[] d = request.getDatetime().split("/");
                        String guess1 = target.get(0).getName();
                        String guess2 = target.get(1).getName();
                        if ((guess1.contains("start") || guess1.contains("min")) && (guess2.contains("end") || guess2.contains("max"))) {
                            return String.format("CQL_FILTER=%s%s >= %s AND %s <= %s", cql, guess1, d[0], guess2, d[1]);
                        }
                        if ((guess2.contains("start") || guess2.contains("min")) && (guess1.contains("end") || guess1.contains("max"))) {
                            return String.format("CQL_FILTER=%s%s >= %s AND %s <= %s", cql, guess2, d[0], guess2, d[1]);
                        }
                    } else {
                        // Only 1 field so use it.
                        log.debug("Map datetime field to name to [{}]", target.get(0).getName());
                        return String.format("CQL_FILTER=%s%s DURING %s", cql, target.get(0).getName(), request.getDatetime());
                    }
                }
                log.error("No date time field found from query for uuid {}, result will not be bounded by date time", uuid);
            } catch (DownloadableFieldsNotFoundException dfnf) {
                // Without field, we cannot create a valid CQL filte targeting a dateTime, so just return empty
                return "";
            }
        }
        return "";
    }
    /**
     * Create the full WMS url to fetch the tiles image
     * @param url - The url from the metadata, it may point to the wms server only without specifying the remain details, this function will do a smart lookup
     * @param uuid - The UUID of the metadata which use to find the WFS links
     * @param request - The request like bbox and other param say datetime, layerName (where layerName is not reliable and need lookup internally)
     * @return - The final URl to do the query
     */
    protected List<String> createMapQueryUrl(String url, String uuid, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if (components.getPath() != null) {
                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>();

                    if (pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("wms")) {
                        param.putAll(wmsDefaultParam.getWms());
                    } else if (pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms")) {
                        param.putAll(wmsDefaultParam.getNcwms());
                    }

                    // Now we add the missing argument from the request
                    param.put("LAYERS", request.getLayerName());
                    param.put("BBOX", request.getBbox().stream().map(BigDecimal::toString).collect(Collectors.joining(",")));

                    // Very specific to IMOS, if we see geoserver-123.aodn.org.au/geoserver/wms, then
                    // we should try cache server -> https://tilecache.aodn.org.au/geowebcache/service/wms, if not work fall back
                    List<String> urls = new ArrayList<>();
                    if (components.getHost() != null
                            && components.getHost().equalsIgnoreCase("geoserver-123.aodn.org.au")
                            && components.getPath().equalsIgnoreCase("/geoserver/wms")) {

                        UriComponentsBuilder builder = UriComponentsBuilder
                                .fromUriString("https://tilecache.aodn.org.au/geowebcache/service/wms");

                        param.forEach((key, value) -> {
                            if (value != null) {
                                builder.queryParam(key, value);
                            }
                        });
                        // Cannot set cql in param as it contains value like "/" which is not allow in UriComponent checks
                        // but server must use "/" in param and cannot encode it to %2F, so to avoid exception in the
                        // build() call, we append the cql after the construction.
                        String target = String.join("&", builder.build().toUriString(), createCQLFilter(uuid, request));
                        log.debug("Cache url to wms geoserver {}", target);
                        urls.add(target);
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
                    // Cannot set cql in param as it contains value like "/" which is not allow in UriComponent checks
                    // but server must use "/" in param and cannot encode it to %2F, so to avoid exception in the
                    // build() call, we append the cql after the construction.
                    String target = String.join("&", builder.build().toUriString(), createCQLFilter(uuid, request));
                    log.debug("Url to wms geoserver {}", target);
                    urls.add(target);

                    return urls;
                }
            }
        } catch (Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }

    protected List<String> createMapDescribeUrl(String url, String uuid, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if (components.getPath() != null) {
                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>(wmsDefaultParam.getDescLayer());

                    // Now we add the missing argument from the request
                    param.put("LAYERS", request.getLayerName());

                    // Very specific to IMOS, if we see geoserver-123.aodn.org.au/geoserver/wms, then
                    // we should try cache server -> https://tilecache.aodn.org.au/geowebcache/service/wms, if not work fall back
                    List<String> urls = new ArrayList<>();
                    if (components.getHost() != null
                            && components.getHost().equalsIgnoreCase("geoserver-123.aodn.org.au")
                            && components.getPath().equalsIgnoreCase("/geoserver/wms")) {

                        UriComponentsBuilder builder = UriComponentsBuilder
                                .fromUriString("https://tilecache.aodn.org.au/geowebcache/service/wms");

                        param.forEach((key, value) -> {
                            if (value != null) {
                                builder.queryParam(key, value);
                            }
                        });
                        String target = builder.build().toUriString();
                        log.debug("Cache url wms geoserver for describe layer {}", target);
                        urls.add(target);
                    }
                    // This is the normal route
                    UriComponentsBuilder builder = UriComponentsBuilder
                            .newInstance()
                            .scheme(components.getScheme())
                            .port(components.getPort())
                            .host(components.getHost())
                            .path(components.getPath());

                    param.forEach((key, value) -> {
                        if (value != null) {
                            builder.queryParam(key, value);
                        }
                    });
                    String target = builder.build().toUriString();
                    log.debug("Url to wms geoserver for describe layer {}", target);
                    urls.add(target);

                    if (pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms") && request.getLayerName().contains("/")) {
                        // Special handle for ncwms, the layer name may be incorrect with /xxx suffix
                        // for example srs_ghrsst_l4_gamssa_url/analysed_sst, we need to remove the /xxxx
                        // Generate more url to test which one works
                        String[] s = request.getLayerName().split("/");
                        FeatureRequest fixed = FeatureRequest.builder().layerName(s[0]).build();
                        urls.addAll(createMapDescribeUrl(url, uuid, fixed));
                    }
                    return urls;
                }
            }
        } catch (Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }

    protected List<String> createMapFeatureQueryUrl(String url, String uuid, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if (components.getPath() != null) {
                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>();

                    if (pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("wms")) {
                        param.putAll(wmsDefaultParam.getWfs());
                    } else if (pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms")) {
                        param.putAll(wmsDefaultParam.getNcwfs());
                    }

                    // Now we add the missing argument from the request
                    param.put("LAYERS", request.getLayerName());
                    param.put("QUERY_LAYERS", request.getLayerName());
                    param.put("WIDTH", request.getWidth().toString());
                    param.put("HEIGHT", request.getHeight().toString());
                    param.put("X", request.getX().toString());
                    param.put("Y", request.getY().toString());
                    param.put("I", request.getX().toString());  // Same as X but some later protocol use I
                    param.put("J", request.getY().toString());  // Same as Y but some later protocol use J
                    param.put("BBOX", request.getBbox().stream().map(BigDecimal::toString).collect(Collectors.joining(",")));

                    // Very specific to IMOS, if we see geoserver-123.aodn.org.au/geoserver/wms, then
                    // we should try cache server -> https://tilecache.aodn.org.au/geowebcache/service/wms, if not work fall back
                    List<String> urls = new ArrayList<>();
                    if (components.getHost() != null
                            && components.getHost().equalsIgnoreCase("geoserver-123.aodn.org.au")
                            && components.getPath().equalsIgnoreCase("/geoserver/wms")) {

                        UriComponentsBuilder builder = UriComponentsBuilder
                                .fromUriString("https://tilecache.aodn.org.au/geowebcache/service/wms");

                        param.forEach((key, value) -> {
                            if (value != null) {
                                builder.queryParam(key, value);
                            }
                        });
                        String target = builder.build().toUriString();
                        log.debug("Cache url to wfs geoserver {}", target);
                        urls.add(target);
                    }
                    // This is the normal route
                    UriComponentsBuilder builder = UriComponentsBuilder
                            .newInstance()
                            .scheme(components.getScheme())
                            .port(components.getPort())
                            .host(components.getHost())
                            .path(components.getPath());

                    param.forEach((key, value) -> {
                        if (value != null) {
                            builder.queryParam(key, value);
                        }
                    });
                    String target = builder.build().toUriString();
                    log.debug("Url to wfs geoserver {}", target);
                    urls.add(target);

                    return urls;
                }
            }
        } catch (Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }

    /**
     * Find the wms server url from the metadata based on uuid, this for sure we will not redirect call to some
     * unknown place
     *
     * @param collectionId - The uuid
     * @param request      - The request containing optional layer name
     * @return - The wms server link.
     */
    protected Optional<String> getMapServerUrl(String collectionId, FeatureRequest request) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if (!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);

            String layerName = request != null ? request.getLayerName() : null;
            // Filter for WMS links
            List<LinkModel> wmsLinks = model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains("Data Access > wms"))
                    .toList();

            if (wmsLinks.isEmpty()) {
                log.warn("No WMS links found for collectionId: {}", collectionId);
                return Optional.empty();
            }

            Optional<String> matchedUrl;

            if (layerName != null && !layerName.isEmpty()) {
                // If layer name provided, try to match by layer name
                matchedUrl = wmsLinks.stream()
                        .filter(link -> link.getTitle() != null && link.getTitle().equalsIgnoreCase(layerName))
                        .map(LinkModel::getHref)
                        .findFirst();

                if (matchedUrl.isPresent()) {
                    log.debug("Found WMS link matching layer name: '{}'", layerName);
                    return matchedUrl;
                }
            } else {
                log.debug("No layer name provided, using first WMS link");
            }

            // Fallback: return the first WMS link
            return wmsLinks.stream()
                    .map(LinkModel::getHref)
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    public FeatureInfoResponse getMapFeatures(String collectionId, FeatureRequest request) throws JsonProcessingException, URISyntaxException {

        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);

        if (mapServerUrl.isPresent()) {
            List<String> urls = createMapFeatureQueryUrl(mapServerUrl.get(), collectionId, request);
            // Try one by one, we exit when any works
            for (String url : urls) {
                ResponseEntity<String> response = restTemplateUtils.handleRedirect(url, restTemplate.getForEntity(url, String.class, Collections.emptyMap()), String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    // Now try to unify the return
                    if (MediaType.TEXT_HTML.isCompatibleWith(response.getHeaders().getContentType())) {
                        String html = response.getBody();
                        // This is a simple trick to check if the html is in fact empty body, if empty
                        // try another url
                        if (html != null && html.contains("<div class=\"feature\">")) {
                            return FeatureInfoResponse.builder().html(response.getBody()).build();
                        }
                    } else if (MediaType.APPLICATION_XML.isCompatibleWith(response.getHeaders().getContentType())) {
                        FeatureInfoResponse r = xmlMapper.readValue(response.getBody(), FeatureInfoResponse.class);
                        //  give another url a chance
                        if (!r.getFeatureInfo().isEmpty()) {
                            return r;
                        }
                    }
                }
            }
        }
        return null;
    }

    public DescribeLayerResponse describeLayer(String collectionId, FeatureRequest request) {
        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);

        if (mapServerUrl.isPresent()) {
            List<String> urls = createMapDescribeUrl(mapServerUrl.get(), collectionId, request);
            // Try one by one, we exit when any works
            for (String url : urls) {
                try {
                    ResponseEntity<String> response = restTemplateUtils.handleRedirect(url, restTemplate.getForEntity(url, String.class, Collections.emptyMap()), String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        DescribeLayerResponse layer = xmlMapper.readValue(response.getBody(), DescribeLayerResponse.class);
                        if (layer.getLayerDescription() != null) {
                            return layer;
                        }
                    }
                } catch (RestClientException | URISyntaxException | JsonProcessingException pe) {
                    log.debug("Exception ignored it as we will retry", pe);
                    throw new RuntimeException(pe);
                }
            }
        }
        return null;
    }

    /**
     * Get the wms image/png tile
     *
     * @param collectionId - The uuid
     * @param request      - The request param
     * @return - Must use byte[] to allow cache to disk
     * @throws URISyntaxException - Not expected
     */
    @Cacheable(value = CACHE_WMS_MAP_TILE)
    public byte[] getMapTile(String collectionId, FeatureRequest request) throws URISyntaxException {
        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);
        log.debug("map tile request for uuid {} layername {}", collectionId, request.getLayerName());
        if (mapServerUrl.isPresent()) {
            List<String> urls = createMapQueryUrl(mapServerUrl.get(), collectionId, request);
            // Try one by one, we exit when any works
            for (String url : urls) {
                log.debug("map tile request for layer name {} url {} ", request.getLayerName(), url);
                ResponseEntity<byte[]> response = restTemplateUtils.handleRedirect(url, restTemplate.getForEntity(url, byte[].class, Collections.emptyMap()), byte[].class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                }
            }
        }
        return null;
    }
    /**
     * Query the field using WMS's DescriberLayer function to find out the associated WFS layer and fields
     * @param collectionId - The uuid of the metadata that hold this WMS link
     * @param request - Request item for this WMS layer, usually layer name, size, etc.
     * @return - The fields contained in this WMS layer, we are particular interest in the date time field for subsetting
     */
    public List<DownloadableFieldModel> getDownloadableFields(String collectionId, FeatureRequest request) {
        DescribeLayerResponse response = this.describeLayer(collectionId, request);

        if (response != null && response.getLayerDescription().getWfs() != null) {
            // If we are able to find the wfs server and real layer name based on wms layer, then use it
            FeatureRequest modified = FeatureRequest.builder().layerName(response.getLayerDescription().getQuery().getTypeName()).build();
            return wfsServer.getDownloadableFields(collectionId, modified, response.getLayerDescription().getWfs());
        } else {
            // We trust what is found inside the elastic search metadata
            return wfsServer.getDownloadableFields(collectionId, request, null);
        }
    }
    /**
     * Fetch raw layers from WMS GetCapabilities - cached by URL, that is query all layer supported by this WMS server.
     * This allows multiple collections sharing the same WMS server to use cached results
     *
     * @param wmsServerUrl - The WMS server base URL
     * @return - List of all LayerInfo objects from GetCapabilities (unfiltered)
     */
    @Cacheable(value = au.org.aodn.ogcapi.server.core.configuration.CacheConfig.GET_CAPABILITIES_WMS_LAYERS)
    public List<LayerInfo> fetchCapabilitiesLayersByUrl(String wmsServerUrl) {
        try {
            // Parse the base URL to construct GetCapabilities request
            UriComponents components = UriComponentsBuilder.fromUriString(wmsServerUrl).build();

            // Build GetCapabilities URL
            UriComponentsBuilder builder = UriComponentsBuilder
                    .newInstance()
                    .scheme(components.getScheme())
                    .port(components.getPort())
                    .host(components.getHost())
                    .path(components.getPath() != null ? components.getPath() : "/geoserver/ows")
                    .queryParam("service", "wms")
                    .queryParam("request", "GetCapabilities");

            String url = builder.build().toUriString();
            log.debug("GetCapabilities URL: {}", url);

            // Make the HTTP call
            ResponseEntity<String> response = restTemplateUtils.handleRedirect(
                    url,
                    restTemplate.getForEntity(url, String.class, Collections.emptyMap()),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse XML response
                GetCapabilitiesResponse capabilitiesResponse = xmlMapper.readValue(
                        response.getBody(),
                        GetCapabilitiesResponse.class
                );

                // Extract all layers
                if (capabilitiesResponse != null
                        && capabilitiesResponse.getCapability() != null
                        && capabilitiesResponse.getCapability().getRootLayer() != null
                        && capabilitiesResponse.getCapability().getRootLayer().getLayers() != null) {

                    List<LayerInfo> layers = capabilitiesResponse.getCapability()
                            .getRootLayer()
                            .getLayers();

                    log.info("Fetched and cached get-capabilities layers {} ", layers);
                    return layers;
                }
            }
        } catch (RestClientException | URISyntaxException | JsonProcessingException e) {
            log.error("Error fetching GetCapabilities for URL: {}", wmsServerUrl, e);
            throw new RuntimeException(e);
        }

        return Collections.emptyList();
    }
    /**
     * Get filtered layers from WMS GetCapabilities for a specific collection
     * First fetches all layers (cached by URL), then filters by WFS links (cached by UUID)
     *
     * @param collectionId - The uuid
     * @param request      - The request param
     * @return - List of LayerInfo objects filtered by WFS link matching
     */
    public List<LayerInfo> getCapabilitiesLayers(String collectionId, FeatureRequest request) {
        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);

        if (mapServerUrl.isPresent()) {
            // Fetch all layers from GetCapabilities (this call is cached by URL)
            List<LayerInfo> allLayers = self.fetchCapabilitiesLayersByUrl(mapServerUrl.get());

            if (!allLayers.isEmpty()) {
                // Filter layers based on WFS link matching
                List<LayerInfo> filteredLayers = wfsServer.filterLayersByWfsLinks(collectionId, allLayers);
                log.debug("Returning filteredLayers {}", filteredLayers);

                return filteredLayers;
            }
        }

        return Collections.emptyList();
    }
}
