package au.org.aodn.ogcapi.server.core.model.ogc.wfs;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JacksonXmlRootElement(localName = "WFS_Capabilities", namespace = "http://www.opengis.net/wfs/2.0")
public class WfsGetCapabilitiesResponse {

    @JacksonXmlProperty(localName = "FeatureTypeList")
    private FeatureTypeList featureTypeList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureTypeList {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "FeatureType")
        private List<FeatureTypeInfo> featureTypes;
    }
}
