package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JacksonXmlRootElement(localName = "FeatureType")
public class FeatureTypeInfo {

    @JacksonXmlProperty(localName = "Name")
    protected String name;

    @JacksonXmlProperty(localName = "Title")
    protected String title;

    @JsonProperty("abstract")
    @JacksonXmlProperty(localName = "Abstract")
    private String abstract_;

    @JacksonXmlProperty(localName = "Keywords", namespace = "http://www.opengis.net/ows/1.1")
    private Keywords keywords;

    @JacksonXmlProperty(localName = "DefaultCRS")
    private String defaultCRS;

    @JacksonXmlProperty(localName = "WGS84BoundingBox", namespace = "http://www.opengis.net/ows/1.1")
    private WGS84BoundingBox wgs84BoundingBox;

    @JacksonXmlProperty(localName = "MetadataURL")
    private MetadataURL metadataURL;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Keywords {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Keyword", namespace = "http://www.opengis.net/ows/1.1")
        private List<String> keyword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WGS84BoundingBox {
        @JacksonXmlProperty(localName = "LowerCorner", namespace = "http://www.opengis.net/ows/1.1")
        private String lowerCorner;

        @JacksonXmlProperty(localName = "UpperCorner", namespace = "http://www.opengis.net/ows/1.1")
        private String upperCorner;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataURL {
        @JacksonXmlProperty(isAttribute = true, localName = "href", namespace = "http://www.w3.org/1999/xlink")
        private String href;
    }
}
