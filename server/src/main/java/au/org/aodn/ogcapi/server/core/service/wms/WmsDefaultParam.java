package au.org.aodn.ogcapi.server.core.service.wms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wms-default-param")
public class WmsDefaultParam {
    private Map<String, String> wfs;
    private Map<String, String> ncwfs;

    private Map<String, String> wms;
    private Map<String, String> ncwms;

    private Map<String, String> descLayer;
}
