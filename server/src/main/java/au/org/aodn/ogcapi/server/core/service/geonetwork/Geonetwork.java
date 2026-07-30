package au.org.aodn.ogcapi.server.core.service.geonetwork;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * This Geonetwork is use by portal to store data catalog, it is not use to remotely access external geonetwork
 */
public class Geonetwork implements ApplicationInfo {

    protected final Map<String, Map<?,?>> appInfo;

    public Geonetwork(GNProperties properties, RestTemplate template) {
        this.appInfo = queryInfo(template, properties.host(), properties.infoPath());
    }

    @Override
    public String getName() {
        Object name = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("name", null);
        return name != null ? name.toString() : null;
    }

    @Override
    public String getVersion() {
        Object version = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("version", null);
        return version != null ? version.toString() : null;
    }

    @Override
    public String getDescription() {
        Object description = this.appInfo.getOrDefault("application", Collections.emptyMap()).getOrDefault("description", null);
        return description != null ? description.toString() : null;
    }
}
