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
public class OnlineResource {
    @JacksonXmlProperty(isAttribute = true, localName = "xlink", namespace = "http://www.w3.org/2000/xmlns/")
    private String xlink;

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/1999/xlink")
    private String type;

    @JacksonXmlProperty(isAttribute = true, localName = "href", namespace = "http://www.w3.org/1999/xlink")
    private String href;
}
