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
public class GeographicBoundingBox {
    @JacksonXmlProperty(localName = "westBoundLongitude")
    private double westBoundLongitude;

    @JacksonXmlProperty(localName = "eastBoundLongitude")
    private double eastBoundLongitude;

    @JacksonXmlProperty(localName = "southBoundLatitude")
    private double southBoundLatitude;

    @JacksonXmlProperty(localName = "northBoundLatitude")
    private double northBoundLatitude;
}
