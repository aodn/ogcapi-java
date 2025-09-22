package au.org.aodn.ogcapi.server.core.service.wms;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.wms.FeatureInfo;
import au.org.aodn.ogcapi.server.core.model.wms.FeatureInfoResponse;
import au.org.aodn.ogcapi.server.core.service.ElasticSearchBase;
import au.org.aodn.ogcapi.server.core.service.Search;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class WmsServer {
    protected XmlMapper xmlMapper;
    protected DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.US);

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

    protected List<String> createQueryUrl(String url, FeatureRequest request) {
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
                        param.putAll(wmsDefaultParam.getNcwms());
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
                        log.debug("Cache url to geoserver {}", target);
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
                    log.debug("Url to geoserver {}", target);
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
     * Specific to the geoserver we own, when request wms feature, it return HTML!! which is super bad.
     * This force you to accept the format and styling, so better parse and convert it back to json
     * @param html - HTML string from geoserver
     * @return Object represent the content.
     */
    protected FeatureInfoResponse convertFromHtml(String html) {
        FeatureInfoResponse result = FeatureInfoResponse.builder().featureInfo(new ArrayList<>()).build();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            // The incoming html is malformed with <br> not self close.
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(html.replaceAll("(?i)<br\\s*>", "<br/>"))));
            doc.getDocumentElement().normalize();
            NodeList divs = doc.getElementsByTagName("div");
            for (int i = 0; i < divs.getLength(); i++) {
                Element div = (Element) divs.item(i);
                if (div.getAttribute("class").equals("featurewhite")) {
                    FeatureInfo featureInfo = new FeatureInfo();
                    NodeList bTags = div.getElementsByTagName("b");
                    for (int j = 0; j < bTags.getLength(); j++) {
                        Element bTag = (Element) bTags.item(j);
                        String key = bTag.getTextContent().trim().replace(" :", "");
                        String value = bTag.getNextSibling() != null ? bTag.getNextSibling().getTextContent().trim() : "";
                        switch (key) {
                            case "Platform number":
                                featureInfo.setPlatformNumber(value);
                                break;
                            case "Data centre":
                                featureInfo.setDataCentre(value);
                                break;
                            case "Profile Processing Mode":
                                featureInfo.setProfileProcessingMode(value);
                                break;
                            case "Oxygen Sensor on float:":
                                featureInfo.setOxygenSensorOnFloat(Boolean.parseBoolean(value));
                                break;
                            case "Date":
                                try {
                                    ZonedDateTime date = ZonedDateTime.parse(value, formatter.withZone(java.time.ZoneId.of("UTC")));
                                    featureInfo.setTime(date);
                                } catch (DateTimeParseException e) {
                                    throw new RuntimeException("Failed to parse date: " + value, e);
                                }
                                break;
                        }
                    }
                    result.getFeatureInfo().add(featureInfo);
                }
            }
        }
        catch(Exception e){
            log.error("Error parsing HTML", e);
        }

        return result;
    }

    public FeatureInfoResponse getMapFeatures(String collectionId, FeatureRequest request) throws JsonProcessingException {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(List.of(collectionId), null);

        if(!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            Optional<String> mapServerUrl = model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains("> wms") && link.getTitle().equalsIgnoreCase(request.getLayerName())) // This is the pattern for wfs link
                    .map(LinkModel::getHref)
                    .findFirst();

            if(mapServerUrl.isPresent()) {
                List<String> urls = createQueryUrl(mapServerUrl.get(), request);
                for(String url : urls) {
                    // Try one by the, we exit when any works
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class, Collections.emptyMap());

                    if(response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
                        // Redirect should happen automatically but it does not so here is a safe guard
                        // the reason happens because http is use but redirect to https
                        response = restTemplate.getForEntity(response.getHeaders().getLocation().toString(), String.class, Collections.emptyMap());
                    }

                    if(response.getStatusCode().is2xxSuccessful()) {
                        // Now try to unify the return
                        if(MediaType.TEXT_HTML.isCompatibleWith(response.getHeaders().getContentType())) {
                            return convertFromHtml(response.getBody());
                        }
                        else if(MediaType.APPLICATION_XML.isCompatibleWith(response.getHeaders().getContentType())) {
                            return xmlMapper.readValue(response.getBody(), FeatureInfoResponse.class);
                        }
                    }
                }
            }
        }
        return null;
    }
}
