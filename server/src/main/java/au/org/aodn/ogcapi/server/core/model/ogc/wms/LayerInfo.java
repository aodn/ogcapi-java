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

class KeywordList {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Keyword")
    private List<String> keyword;
}

class GeographicBoundingBox {
    @JacksonXmlProperty(localName = "westBoundLongitude")
    private double westBoundLongitude;

    @JacksonXmlProperty(localName = "eastBoundLongitude")
    private double eastBoundLongitude;

    @JacksonXmlProperty(localName = "southBoundLatitude")
    private double southBoundLatitude;

    @JacksonXmlProperty(localName = "northBoundLatitude")
    private double northBoundLatitude;
}

class BoundingBox {
    @JacksonXmlProperty(isAttribute = true)
    private String CRS;

    @JacksonXmlProperty(isAttribute = true)
    private double minx;

    @JacksonXmlProperty(isAttribute = true)
    private double miny;

    @JacksonXmlProperty(isAttribute = true)
    private double maxx;

    @JacksonXmlProperty(isAttribute = true)
    private double maxy;
}

class Style {
    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Title")
    private String title;

    @JacksonXmlProperty(localName = "Abstract")
    private String abstract_;

    @JacksonXmlProperty(localName = "LegendURL")
    private LegendURL legendURL;
}

class LegendURL {
    @JacksonXmlProperty(isAttribute = true)
    private int width;

    @JacksonXmlProperty(isAttribute = true)
    private int height;

    @JacksonXmlProperty(localName = "Format")
    private String format;

    @JacksonXmlProperty(localName = "OnlineResource")
    private OnlineResource onlineResource;
}

class OnlineResource {
    @JacksonXmlProperty(isAttribute = true, localName = "xlink", namespace = "http://www.w3.org/2000/xmlns/")
    private String xlink;

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/1999/xlink")
    private String type;

    @JacksonXmlProperty(isAttribute = true, localName = "href", namespace = "http://www.w3.org/1999/xlink")
    private String href;
}