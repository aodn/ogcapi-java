package au.org.aodn.ogcapi.server.core.model.wms;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Data
@Getter
@Setter
public class FeatureInfo {
    @JacksonXmlProperty(localName = "time")
    protected ZonedDateTime time;

    @JacksonXmlProperty(localName = "value")
    protected String value;

    @JacksonXmlProperty(localName = "platform")
    protected String platformNumber;

    @JacksonXmlProperty(localName = "dataCentre")
    protected String dataCentre;

    @JacksonXmlProperty(localName = "profileProcessingMode")
    protected String profileProcessingMode;

    @JacksonXmlProperty(localName = "oxygenSensorOnFloat")
    protected Boolean oxygenSensorOnFloat;
}
