package au.org.aodn.ogcapi.server.core.model.ogc.wms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NcWmsLayerInfo {

    @JsonProperty("title")
    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    @JsonProperty("units")
    private String units;

    @JsonProperty("bbox")
    private List<Double> bbox;

    @JsonProperty("scales")
    private List<Double> scales;

    @JsonProperty("palettes")
    private List<String> palettes;

    @JsonProperty("defaultPalette")
    private String defaultPalette;

    @JsonProperty("timeAxis")
    private List<String> timeAxis;

    @JsonProperty("elevationAxis")
    private List<Double> elevationAxis;

    @JsonProperty("scaleRange")
    private List<Double> scaleRange;

    @JsonProperty("datesWithData")
    private Map<String, Map<String, List<Integer>>> datesWithData;

    @JsonProperty("numColorBands")
    private Integer numColorBands;

    @JsonProperty("supportedStyles")
    private List<String> supportedStyles;

    @JsonProperty("moreInfo")
    private String moreInfo;

    @JsonProperty("timeAxisUnits")
    private String timeAxisUnits;

    @JsonProperty("nearestTimeIso")
    private String nearestTimeIso;

    @JsonProperty("logScaling")
    private boolean logScaling;
}
