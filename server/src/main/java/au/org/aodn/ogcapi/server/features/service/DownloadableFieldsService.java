package au.org.aodn.ogcapi.server.features.service;

import au.org.aodn.ogcapi.server.core.exception.DownloadableFieldsNotFoundException;
import au.org.aodn.ogcapi.server.features.model.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DownloadableFieldsService {

    @Autowired
    private RestTemplate restTemplate;

    private final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Get downloadable fields for a layer
     * @param wfsUrl The WFS server URL
     * @param typeName The WFS type name
     * @return List of downloadable fields
     */
    public List<DownloadableField> getDownloadableFields(String wfsUrl, String typeName) {
        log.info("Getting downloadable fields for typeName: {} from WFS: {}", typeName, wfsUrl);

        try {
            List<DownloadableField> fields = getFilterFieldsFromWfs(wfsUrl, typeName);

            if (fields.isEmpty()) {
                throw new DownloadableFieldsNotFoundException(
                    String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, wfsUrl)
                );
            }

            return fields;
        } catch (Exception e) {
            log.error("Error getting downloadable fields for typeName: {} from WFS: {}", typeName, wfsUrl, e);
            throw new DownloadableFieldsNotFoundException(
                String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, wfsUrl)
            );
        }
    }





    /**
     * Get filter fields from WFS DescribeFeatureType
     */
    private List<DownloadableField> getFilterFieldsFromWfs(String wfsUrl, String typeName) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(wfsUrl)
                    .queryParam("service", "WFS")
                    .queryParam("version", "1.0.0")
                    .queryParam("request", "DescribeFeatureType")
                    .queryParam("typeName", typeName)
                    .build()
                    .toUri();

            log.debug("WFS DescribeFeatureType request: {}", uri);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, null, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                WfsDescribeFeatureTypeResponse wfsResponse = xmlMapper.readValue(response.getBody(), WfsDescribeFeatureTypeResponse.class);
                return convertWfsResponseToDownloadableFields(wfsResponse);
            } else {
                throw new DownloadableFieldsNotFoundException(
                    String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, wfsUrl)
                );
            }

        } catch (Exception e) {
            log.error("Error calling WFS DescribeFeatureType for typeName: {}", typeName, e);
            throw new DownloadableFieldsNotFoundException(
                String.format("No downloadable fields found for typeName '%s' from WFS server '%s'", typeName, wfsUrl)
            );
        }
    }



    /**
     * Convert WFS response to downloadable fields (geometry and date/time fields)
     */
    private List<DownloadableField> convertWfsResponseToDownloadableFields(WfsDescribeFeatureTypeResponse wfsResponse) {
        List<DownloadableField> fields = new ArrayList<>();

        if (wfsResponse.getComplexTypes() != null) {
            for (WfsDescribeFeatureTypeResponse.ComplexType complexType : wfsResponse.getComplexTypes()) {
                if (complexType.getComplexContent() != null &&
                    complexType.getComplexContent().getExtension() != null &&
                    complexType.getComplexContent().getExtension().getSequence() != null) {

                    List<WfsDescribeFeatureTypeResponse.Element> elements = complexType.getComplexContent().getExtension().getSequence().getElements();

                    if (elements != null) {
                        for (WfsDescribeFeatureTypeResponse.Element element : elements) {
                            if (element.getName() != null && element.getType() != null) {

                                // Add geometry fields
                                if ("gml:GeometryPropertyType".equals(element.getType())) {
                                    DownloadableField geomField = new DownloadableField();
                                    geomField.setLabel(element.getName());
                                    geomField.setType("geometrypropertytype");
                                    geomField.setName(element.getName());
                                    fields.add(geomField);
                                }

                                // Add date/time fields
                                else if ("xsd:dateTime".equals(element.getType())) {
                                    DownloadableField timeField = new DownloadableField();
                                    timeField.setLabel(element.getName());
                                    timeField.setType("dateTime");
                                    timeField.setName(element.getName());
                                    fields.add(timeField);
                                }
                                else if ("xsd:date".equals(element.getType())) {
                                    DownloadableField dateField = new DownloadableField();
                                    dateField.setLabel(element.getName());
                                    dateField.setType("date");
                                    dateField.setName(element.getName());
                                    fields.add(dateField);
                                }
                                else if ("xsd:time".equals(element.getType())) {
                                    DownloadableField timeField = new DownloadableField();
                                    timeField.setLabel(element.getName());
                                    timeField.setType("time");
                                    timeField.setName(element.getName());
                                    fields.add(timeField);
                                }
                            }
                        }
                    }
                }
            }
        }

        return fields;
    }
}
