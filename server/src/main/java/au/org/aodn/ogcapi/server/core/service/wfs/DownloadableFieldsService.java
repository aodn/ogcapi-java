package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.model.LinkModel;
import au.org.aodn.ogcapi.server.core.model.StacCollectionModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.WfsDescribeFeatureTypeResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DownloadableFieldsService extends WmsWfsBase {

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected Search search;

    @Autowired
    protected WfsDefaultParam wfsDefaultParam;

    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper;

    public DownloadableFieldsService() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule()); // Add JavaTimeModule
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Cacheable(value = "downloadable-fields")
    public List<DownloadableFieldModel> getDownloadableFields(String collectionId, FeatureRequest request) {

        Optional<String> mapFeatureUrl = getFeatureServerUrl(collectionId, request);

        if(mapFeatureUrl.isPresent()) {
            try {
                String uri = this.createFeatureFieldQueryUrl(mapFeatureUrl.get(), request);
                if (uri != null) {
                    log.debug("Url to wfs {}", uri);
                    ResponseEntity<String> response = handleRedirect(uri, restTemplate.getForEntity(uri, String.class), String.class);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        return convertWfsResponseToDownloadableFields(
                                xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class)
                        );
                    } else {
                        throw new DownloadableFieldsNotFoundException(
                                String.format("No downloadable fields found for call '%s'", uri)
                        );
                    }
                }
            }
            catch (URISyntaxException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            catch (DownloadableFieldsNotFoundException de) {
                throw de;
            }
            catch (RuntimeException re) {
                throw new DownloadableFieldsNotFoundException("No downloadable fields found due to remote connection timeout");
            }
        }
        return List.of();
    }
    /**
     * Find the wms server url from the metadata based on uuid, this for sure we will not redirect call to some
     * unknown place
     * @param collectionId - The uuid
     * @return - The wms server link.
     */
    protected Optional<String> getFeatureServerUrl(String collectionId, FeatureRequest request) {
        // Get the record contains the map feature, given one uuid , 1 result expected
        ElasticSearchBase.SearchResult<StacCollectionModel> result = search.searchCollections(collectionId);
        if(!result.getCollections().isEmpty()) {
            StacCollectionModel model = result.getCollections().get(0);
            return model.getLinks()
                    .stream()
                    .filter(link -> link.getAiGroup() != null)
                    .filter(link -> link.getAiGroup().contains("Data Access > wfs") && link.getTitle().equalsIgnoreCase(request.getLayerName())) // This is the pattern for wfs link
                    .map(LinkModel::getHref)
                    .findFirst();
        }
        else {
            return Optional.empty();
        }
    }

    protected String createFeatureFieldQueryUrl(String url, FeatureRequest request) {
        UriComponents components = UriComponentsBuilder.fromUriString(url).build();
        if(components.getPath() != null) {
            // Now depends on the service, we need to have different arguments
            List<String> pathSegments = components.getPathSegments();
            if (!pathSegments.isEmpty()) {
                Map<String, String> param = new HashMap<>(wfsDefaultParam.getFields());

                // Now we add the missing argument from the request
                param.put("TYPENAME", request.getLayerName());

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

                return target;
            }
        }
        return null;
    }
    /**
     * Convert WFS response to downloadable fields (geometry and date/time fields)
     */
    protected List<DownloadableFieldModel> convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
        return wfsResponse.getComplexTypes() != null ?
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
                        .map(this::createDownloadableField)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()) : new ArrayList<>();
    }
    /**
     * Create a downloadable field based on the element type
     */
    protected DownloadableFieldModel createDownloadableField(WfsDescribeFeatureTypeResponse.Element element) {
        String elementType = element.getType();
        if (elementType == null) {
            return null;
        }

        DownloadableFieldModel field = DownloadableFieldModel
                .builder()
                .label(element.getName())
                .name(element.getName())
                .build();

        return switch (elementType) {
            case "gml:GeometryPropertyType" -> {
                field.setType("geometrypropertytype");
                yield field;
            }
            case "xsd:dateTime" -> {
                field.setType("dateTime");
                yield field;
            }
            case "xsd:date" -> {
                field.setType("date");
                yield field;
            }
            case "xsd:time" -> {
                field.setType("time");
                yield field;
            }
            default -> null; // Ignore other types
        };
    }
}
