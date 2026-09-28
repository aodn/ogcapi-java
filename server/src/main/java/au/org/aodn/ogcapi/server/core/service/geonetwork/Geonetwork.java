package au.org.aodn.ogcapi.server.core.service.geonetwork;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * This Geonetwork is use by portal to store data catalog, it is not use to remotely access external geonetwork
 */
public class Geonetwork implements ApplicationInfo {

    protected final InfoCache appInfo;

    public Geonetwork(GNProperties properties, RestTemplate template) {
        this.appInfo = new InfoCache(() -> queryInfo(template, properties.host(), properties.infoPath()));
    }

    @Override
    public Map<String, Map<?,?>> getAppInfo() {
        return appInfo.get();
    }
}
