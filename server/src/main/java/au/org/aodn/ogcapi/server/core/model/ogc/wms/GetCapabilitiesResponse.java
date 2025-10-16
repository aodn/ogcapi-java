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
@JacksonXmlRootElement(localName = "WMS_Capabilities")
public class GetCapabilitiesResponse {
    
    @JacksonXmlProperty(localName = "Capability")
    private Capability capability;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capability {
        
        @JacksonXmlProperty(localName = "Layer")
        private RootLayer rootLayer;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootLayer {
        
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Layer")
        private List<LayerInfo> layers;
    }
}
