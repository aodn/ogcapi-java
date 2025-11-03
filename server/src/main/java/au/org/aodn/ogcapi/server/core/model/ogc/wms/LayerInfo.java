package au.org.aodn.ogcapi.server.core.model.ogc.wms;

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
@JacksonXmlRootElement(localName = "Layer")
public class LayerInfo {

    @JacksonXmlProperty(isAttribute = true)
    private String queryable;

    @JacksonXmlProperty(isAttribute = true)
    private String opaque;

    @JacksonXmlProperty(localName = "Name")
    protected String name;

    @JacksonXmlProperty(localName = "Title")
    protected String title;

    @JacksonXmlProperty(localName = "Abstract")
    private String abstract_;

    @JacksonXmlProperty(localName = "KeywordList")
    private KeywordList keywordList;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "CRS")
    private List<String> crs;

    @JacksonXmlProperty(localName = "EX_GeographicBoundingBox")
    private GeographicBoundingBox geographicBoundingBox;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "BoundingBox")
    private List<BoundingBox> boundingBoxes;

    @JacksonXmlProperty(localName = "Style")
    private Style style;
}
