package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JacksonXmlRootElement(localName = "FeatureInfoResponse")
public class FeatureInfoResponse {
    // A special case where the return is a html directly
    protected String html;

    @JacksonXmlProperty(localName = "longitude")
    protected Double longitude;

    @JacksonXmlProperty(localName = "latitude")
    protected Double latitude;

    @JacksonXmlProperty(localName = "iIndex")
    protected Integer iIndex;

    @JacksonXmlProperty(localName = "jIndex")
    protected Integer jIndex;

    @JacksonXmlProperty(localName = "gridCentreLon")
    protected Double gridCentreLon;

    @JacksonXmlProperty(localName = "gridCentreLat")
    protected Double gridCentreLat;

    @JacksonXmlProperty(localName = "FeatureInfo")
    @JacksonXmlElementWrapper(useWrapping = false)
    protected List<FeatureInfo> featureInfo;
}
