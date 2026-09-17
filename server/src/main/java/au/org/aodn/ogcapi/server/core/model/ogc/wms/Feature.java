package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.*;

/**
 * ncWMS wrap the FeatureInfo inside a Feature element, one per layer.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Feature {
    @JacksonXmlProperty(localName = "layer")
    protected String layer;

    @JacksonXmlProperty(localName = "FeatureInfo")
    @JacksonXmlElementWrapper(useWrapping = false)
    protected List<FeatureInfo> featureInfo;
}
