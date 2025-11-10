package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundingBox {
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
