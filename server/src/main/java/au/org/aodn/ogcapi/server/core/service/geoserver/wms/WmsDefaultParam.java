package au.org.aodn.ogcapi.server.core.service.geoserver.wms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wms-default-param")
public class WmsDefaultParam {

    public static final String WMS_LINK_MARKER = "Data Access > wms";

    private Map<String, String> wfs;
    private Map<String, String> ncwfs;

    private Map<String, String> wms;
    private Map<String, String> ncwms;
    private Map<String, String> ncmetadata;

    private Map<String, String> descLayer;

    @JsonProperty("allow-id")
    private Set<String> allowId;
}
