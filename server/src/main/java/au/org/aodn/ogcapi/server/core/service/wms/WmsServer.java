package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class WmsServer {

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected Search search;

    @Autowired
    protected WmsDefaultParam wmsDefaultParam;

    protected List<String> createQueryUrl(String url, FeatureRequest request) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if(components.getPath() != null) {
                Map<String, String> queryParams = components
                        .getQueryParams()
                        .toSingleValueMap();

                // Now depends on the service, we need to have different arguments
                List<String> pathSegments = components.getPathSegments();
                if (!pathSegments.isEmpty()) {
                    Map<String, String> param = new HashMap<>();

                    if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("wms")) {
                        param.putAll(wmsDefaultParam.getWfs());
                    }
                    else if(pathSegments.get(pathSegments.size() - 1).equalsIgnoreCase("ncwms")) {
                        param.putAll(wmsDefaultParam.getNcwms());
                    }
                    // Now we add the missing argument from the request
                    param.put("LAYERS", request.getLayerName());
                    param.put("QUERY_LAYERS", request.getLayerName());
                    param.put("WIDTH", request.getWidth().toString());
                    param.put("HEIGHT", request.getHeight().toString());
                    param.put("X", request.getX().toString());
                    param.put("Y", request.getY().toString());
                    param.put("I", request.getX().toString());
                    param.put("J", request.getY().toString());
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
                        urls.add(builder.build().toUriString());
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
                    urls.add(builder.build().toUriString());

                    return urls;
                }
            }
        }
        catch(Exception e) {
            log.error("URL syntax error {}", url, e);
        }
        return null;
    }

    public String getMapFeatures(String collectionId, FeatureRequest request) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(List.of(collectionId), null);

        if(!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            Optional<String> mapServerUrl = model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup().contains("> wfs")) // This is the pattern for wfs link
                    .map(LinkModel::getHref)
                    .findFirst();

            if(mapServerUrl.isPresent()) {
                List<String> urls = createQueryUrl(mapServerUrl.get(), request);
                for(String url : urls) {
                    // Try one by the, we exit when any works
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, Collections.emptyMap());
                    if(response.getStatusCode().is2xxSuccessful()) {
                        return response.getBody();
                    }
                }
            }
        }
        return null;
    }
}
