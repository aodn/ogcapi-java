package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "schema", namespace = "http://www.w3.org/2001/XMLSchema")
public class WfsDescribeFeatureTypeResponse {

    @JacksonXmlProperty(localName = "complexType")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ComplexType> complexTypes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComplexType {
        @JacksonXmlProperty(isAttribute = true)
        private String name;

        @JacksonXmlProperty(localName = "complexContent")
        private ComplexContent complexContent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComplexContent {
        @JacksonXmlProperty(localName = "extension")
        private Extension extension;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Extension {
        @JacksonXmlProperty(localName = "sequence")
        private Sequence sequence;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sequence {
        @JacksonXmlProperty(localName = "element")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<Element> elements;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Element {
        @JacksonXmlProperty(isAttribute = true)
        private String name;

        @JacksonXmlProperty(isAttribute = true)
        private String type;
    }
}
