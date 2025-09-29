package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "WMS_DescribeLayerResponse")
public class DescribeLayerResponse {

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Query {
        @JacksonXmlProperty(isAttribute = true)
        private String typeName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LayerDescription {
        @JacksonXmlProperty(isAttribute = true)
        private String name;

        @JacksonXmlProperty(isAttribute = true)
        private String wfs;

        @JacksonXmlProperty(isAttribute = true)
        private String owsURL;

        @JacksonXmlProperty(isAttribute = true)
        private String owsType;

        @JacksonXmlProperty(localName = "Query")
        private Query query;
    }

    @JacksonXmlProperty(isAttribute = true)
    private String version;

    @JacksonXmlProperty(localName = "LayerDescription")
    @JacksonXmlElementWrapper(useWrapping = false)
    private LayerDescription layerDescription;
}
