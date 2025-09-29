package au.org.aodn.ogcapi.server.core.model.ogc.wms;

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
}
