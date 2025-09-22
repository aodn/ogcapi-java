package au.org.aodn.ogcapi.server.core.model.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JacksonXmlRootElement(localName = "FeatureInfoResponse")
public class FeatureInfoResponse {
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
    protected FeatureInfo featureInfo;
}
