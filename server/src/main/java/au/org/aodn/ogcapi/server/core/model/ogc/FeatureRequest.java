package au.org.aodn.ogcapi.server.core.model.ogc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Query parameters for feature requests")
@Data
@Builder
@EqualsAndHashCode
public class FeatureRequest implements Serializable {
    @Schema(description = "Property to be return")
    private List<String> properties;

    @Schema(description = "Only records that have a geometry that intersects the bounding box are selected. The bounding box is provided as four or six numbers, depending on whether the coordinate reference system includes a vertical axis (height or depth):  * Lower left corner, coordinate axis 1 * Lower left corner, coordinate axis 2 * Minimum value, coordinate axis 3 (optional) * Upper right corner, coordinate axis 1 * Upper right corner, coordinate axis 2 * Maximum value, coordinate axis 3 (optional)  The coordinate reference system of the values is WGS 84 long/lat (http://www.opengis.net/def/crs/OGC/1.3/CRS84) unless a different coordinate reference system is specified in the parameter `bbox-crs`.  For WGS 84 longitude/latitude the values are in most cases the sequence of minimum longitude, minimum latitude, maximum longitude and maximum latitude.  However, in cases where the box spans the antimeridian the first value (west-most box edge) is larger than the third value (east-most box edge).  If the vertical axis is included, the third and the sixth number are the bottom and the top of the 3-dimensional bounding box.  If a record has multiple spatial geometry properties, it is the decision of the server whether only a single spatial geometry property is used to determine the extent or all relevant geometries.")
    private List<BigDecimal> bbox;

    @Schema(description = "Either a date-time or an interval, open or closed. Date and time expressions adhere to RFC 3339. Open intervals are expressed using double-dots.  Examples:  * A date-time: \"2018-02-12T23:20:50Z\" * A closed interval: \"2018-02-12T00:00:00Z/2018-03-18T12:31:12Z\" * Open intervals: \"2018-02-12T00:00:00Z/..\" or \"../2018-03-18T12:31:12Z\"  Only records that have a temporal property that intersects the value of `datetime` are selected.  It is left to the decision of the server whether only a single temporal property is used to determine the extent or all relevant temporal properties.")
    private String datetime;

    @Schema(description = "WFS server URL (required when featureId is 'downloadableFields')")
    private String serverUrl;

    @Schema(description = "WFS type name (required when featureId is 'downloadableFields')")
    private String layerName;

    @Schema(description = "Width")
    private BigDecimal width;

    @Schema(description = "Height")
    private BigDecimal height;

    @Schema(description = "X")
    private BigDecimal x;

    @Schema(description = "Y")
    private BigDecimal y;

    @Schema(description = "Wave buoy name")
    private String waveBuoy;
}
