package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WfsDescribeFeatureTypeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DownloadableFieldsService {

    @Autowired
    protected WfsDefaultParam wfsDefaultParam;

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
                log.debug("Url query support field in wfs {}", target);

                return target;
            }
        }
        return null;
    }
    /**
     * Convert WFS response to downloadable fields (geometry and date/time fields)
     */
    protected static List<DownloadableFieldModel> convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
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
                        .map(DownloadableFieldsService::createDownloadableField)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()) : new ArrayList<>();
    }
    /**
     * Create a downloadable field based on the element type
     */
    protected static DownloadableFieldModel createDownloadableField(WfsDescribeFeatureTypeResponse.Element element) {
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
