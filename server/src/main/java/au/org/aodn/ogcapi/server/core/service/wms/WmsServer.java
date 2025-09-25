package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class WmsServer {
    protected XmlMapper xmlMapper;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected Search search;

    @Autowired
    protected WmsDefaultParam wmsDefaultParam;

    public WmsServer() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    protected List<String> createMapQueryUrl(String url, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if(components.getPath() != null) {
                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>();

                    if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("wms")) {
                        param.putAll(wmsDefaultParam.getWms());
                    }
                    else if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms")) {
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
                            if(value != null) {
                                builder.queryParam(key, value);
                            }
                        });
                        String target = builder.build().toUriString();
                        log.debug("Cache url to wms geoserver {}", target);
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
                        if(value != null) {
                            builder.queryParam(key, value);
                        }
                    });
                    String target = builder.build().toUriString();
                    log.debug("Url to wms geoserver {}", target);
                    urls.add(target);

                    return urls;
                }
            }
        }
            catch(Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }

    protected List<String> createMapFeatureQueryUrl(String url, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if(components.getPath() != null) {
                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>();

                    if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("wms")) {
                        param.putAll(wmsDefaultParam.getWfs());
                    }
                    else if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms")) {
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
                            if(value != null) {
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
                        if(value != null) {
                            builder.queryParam(key, value);
                        }
                    });
                    String target = builder.build().toUriString();
                    log.debug("Url to wfs geoserver {}", target);
                    urls.add(target);

                    return urls;
                }
            }
        }
        catch(Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }
    /**
     * Find the wms server url from the metadata based on uuid, this for sure we will not redirect call to some
     * unknown place
     * @param collectionId - The uuid
     * @return - The wms server link.
     */
    @Cacheable("wms-server-url")
    protected Optional<String> getMapServerUrl(String collectionId, FeatureRequest request) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(List.of(collectionId), null);
        if(!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains("Data Access > wms") && link.getTitle().equalsIgnoreCase(request.getLayerName())) // This is the pattern for wfs link
                    .map(LinkModel::getHref)
                    .findFirst();
        }
        else {
            return Optional.empty();
        }
    }
    /**
     * We may get http to https response from our geoserver, so this is make sure we redirect call, although we get the url
     * from metadata, we also want to make sure the redirect is to the same server.
     * @param sourceUrl - url from metadata
     * @param response - the original that may or may not have the redirect
     * @param type - The type of response
     * @return - If it is redirect, then call the redirect location given host check, if not same response return
     * @param <T> The type of the return type
     * @throws URISyntaxException - Not expect to throw
     */
    protected <T> ResponseEntity<T> handleRedirect(String sourceUrl, ResponseEntity<T> response, Class<T> type) throws URISyntaxException {
        if(response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
            // Redirect should happen automatically but it does not so here is a safe-guard
            // the reason happens because http is use but redirect to https
            URI source = new URI(sourceUrl);
            URI redirect = response.getHeaders().getLocation();
            if(redirect.getHost().equalsIgnoreCase(source.getHost())) {
                // Only allow redirect to same server.
                return restTemplate.getForEntity(response.getHeaders().getLocation().toString(), type, Collections.emptyMap());
            }
            else {
                log.error("Redirect to different host not allowed, from {} to {}", source, redirect);
            }
        }
        return response;
    }

    public FeatureInfoResponse getMapFeatures(String collectionId, FeatureRequest request) throws JsonProcessingException, URISyntaxException {

        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);

        if(mapServerUrl.isPresent()) {
            List<String> urls = createMapFeatureQueryUrl(mapServerUrl.get(), request);
            // Try one by one, we exit when any works
            for(String url : urls) {
                ResponseEntity<String> response = handleRedirect(url, restTemplate.getForEntity(url, String.class, Collections.emptyMap()), String.class);
                if(response.getStatusCode().is2xxSuccessful()) {
                    // Now try to unify the return
                    if(MediaType.TEXT_HTML.isCompatibleWith(response.getHeaders().getContentType())) {
                        String html = response.getBody();
                        // This is a simple trick to check if the html is in fact empty body, if empty
                        // try another url
                        if(html != null && html.contains("<div class=\"feature\">")) {
                            return FeatureInfoResponse.builder().html(response.getBody()).build();
                        }
                    }
                    else if(MediaType.APPLICATION_XML.isCompatibleWith(response.getHeaders().getContentType())) {
                        FeatureInfoResponse r =  xmlMapper.readValue(response.getBody(), FeatureInfoResponse.class);
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
    /**
     * Get the wms image/png tile
     * @param collectionId - The uuid
     * @param request - The request param
     * @return - A resource likely image/png
     * @throws URISyntaxException - Not expected
     */
    public ResponseEntity<Resource> getMapTile(String collectionId, FeatureRequest request) throws URISyntaxException {

        Optional<String> mapServerUrl = getMapServerUrl(collectionId, request);

        if(mapServerUrl.isPresent()) {
            List<String> urls = createMapQueryUrl(mapServerUrl.get(), request);
            // Try one by one, we exit when any works
            for (String url : urls) {
                ResponseEntity<Resource> response = handleRedirect(url, restTemplate.getForEntity(url, Resource.class, Collections.emptyMap()), Resource.class);
                if(response.getStatusCode().is2xxSuccessful()) {
                    return response;
                }
            }
        }
        return null;
    }
}
