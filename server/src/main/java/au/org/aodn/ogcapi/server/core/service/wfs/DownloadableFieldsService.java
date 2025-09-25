package au.org.aodn.ogcapi.server.core.service.wfs;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.UnauthorizedServerException;
import au.org.aodn.ogcapi.server.core.model.wfs.DownloadableFieldModel;
import au.org.aodn.ogcapi.server.core.model.dto.wfs.WfsDescribeFeatureTypeResponse;
import au.org.aodn.ogcapi.server.core.model.wfs.WfsInfo;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DownloadableFieldsService {

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected WfsServer wfsServer;
    // Cannot use singleton bean as it impacted other dependency
    protected final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Get downloadable fields for a layer
     *
     * @param uuid     The collection UUID
     * @param typeName The WFS type name
     * @return List of downloadable fields
     */
    @Cacheable(value = "downloadable-fields", key = "#uuid + ':' + #typeName")
    public List<DownloadableFieldModel> getDownloadableFields(String uuid, String typeName) {
        log.info("Getting downloadable fields for typeName: {} from uuid: {}", typeName, uuid);
        // Extract WFS URL and layer name from collection links
        WfsInfo wfsInfo = wfsServer.getWfsInfo(uuid, typeName);
        if (wfsInfo == null) {
            throw new RuntimeException("No WFS link found for collection " + uuid + " with layer name " + typeName);
        }

        // Validate and get approved WFS server URL
        String approvedWfsUrl = wfsServer.validateAndGetApprovedServerUrl(wfsInfo.wfsUrl());

        try {
            // Get downloadable fields from WFS
            List<DownloadableFieldModel> fields = getFilterFieldsFromWfs(approvedWfsUrl, wfsInfo.layerName());

            if (fields.isEmpty()) {
                throw new DownloadableFieldsNotFoundException(
                        String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", wfsInfo.layerName(), wfsInfo.wfsUrl())
                );
            }
            return fields;
        } catch (UnauthorizedServerException | DownloadableFieldsNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting downloadable fields for typeName: {} from WFS: {}", wfsInfo.layerName(), wfsInfo.wfsUrl(), e);
            throw new RuntimeException(
                    String.format("Error getting downloadable fields for typeName '%s' from WFS server '%s'", wfsInfo.layerName(), wfsInfo.wfsUrl())
            );
        }
    }


    /**
     * Get filter fields from WFS DescribeFeatureType
     */
//    @Cacheable(value = "downloadable-fields", key = "#wfsUrl + ':' + #typeName")
    public List<DownloadableFieldModel> getFilterFieldsFromWfs(String approvedWfsUrl, String typeName) {
        // SSRF protection: Only use pre-approved server URL is used in the request
        try {
            URI uri = UriComponentsBuilder.fromUriString(approvedWfsUrl)
                    .queryParam("service", "WFS")
                    .queryParam("version", "1.0.0")
                    .queryParam("request", "DescribeFeatureType")
                    .queryParam("typeName", typeName)
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, null, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                WfsDescribeFeatureTypeResponse wfsResponse = xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class);
                return convertWfsResponseToDownloadableFields(wfsResponse);
            } else {
                throw new DownloadableFieldsNotFoundException(
                        String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, approvedWfsUrl)
                );
            }

        } catch (UnauthorizedServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling WFS DescribeFeatureType for typeName: {}", typeName, e);
            throw new DownloadableFieldsNotFoundException(
                    String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, approvedWfsUrl)
            );
        }
    }


    /**
     * Convert WFS response to downloadable fields (geometry and date/time fields)
     */
    private List<DownloadableFieldModel> convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
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
    private DownloadableFieldModel createDownloadableField(WfsDescribeFeatureTypeResponse.Element element) {
        String elementType = element.getType();
        if (elementType == null) {
            return null;
        }

        DownloadableFieldModel field = new DownloadableFieldModel();
        field.setLabel(element.getName());
        field.setName(element.getName());

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
