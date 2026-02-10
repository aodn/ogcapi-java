package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.model.ogc.FeatureRequest;
import au.org.aodn.ogcapi.server.core.model.ogc.wfs.WFSFieldModel;
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
                log.debug("Url query support field in wfs {}", target);

                return target;
            }
        }
        return null;
    }

    /**
     * Convert WFS response to downloadable fields.
     * The typename is extracted from the top-level xsd:element (e.g., <xsd:element name="aatams_sattag_dm_profile_map" .../>)
     */
    protected static List<WFSFieldModel> convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
        String typename;
        if (wfsResponse.getTopLevelElements() != null && !wfsResponse.getTopLevelElements().isEmpty()) {
            typename = wfsResponse.getTopLevelElements().get(0).getName();
        } else {
            typename = null;
        }

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
                        .map(element -> WFSFieldModel.builder()
                                .label(element.getName())
                                .name(element.getName())
                                .type(normalizeType(element.getType()))
                                .typename(typename)
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();
    }

    /**
     * Normalize the XSD type to a simpler type name
     *
     * @param xsdType - The XSD type (e.g., "xsd:dateTime", "gml:GeometryPropertyType")
     * @return - Normalized type name
     */
    protected static String normalizeType(String xsdType) {
        if (xsdType == null) {
            return null;
        }
        return switch (xsdType) {
            case "gml:GeometryPropertyType" -> "geometrypropertytype";
            case "xsd:dateTime" -> "dateTime";
            case "xsd:date" -> "date";
            case "xsd:time" -> "time";
            case "xsd:long" -> "long";
            case "xsd:int" -> "int";
            case "xsd:double" -> "double";
            case "xsd:float" -> "float";
            case "xsd:string" -> "string";
            case "xsd:boolean" -> "boolean";
            default -> xsdType.contains(":") ? xsdType.substring(xsdType.indexOf(":") + 1) : xsdType;
        };
    }
}
