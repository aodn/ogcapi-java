package au.org.aodn.ogcapi.server.core.service.geoserver.wfs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wfs-default-param")
public class WfsDefaultParam {

    public static final String WFS_LINK_MARKER = "Data Access > wfs";

    private Map<String, String> fields;
    private Map<String, String> download;
    private Map<String, String> capabilities;
    private Map<String, String> estimate;
}
