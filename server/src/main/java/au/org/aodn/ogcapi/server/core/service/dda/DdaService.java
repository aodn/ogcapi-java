package au.org.aodn.ogcapi.server.core.service.dda;

import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;
import java.util.Map;

public class DdaService implements ApplicationInfo {
    protected final Map<String, Map<?,?>> appInfo;

    public DdaService(DdaProperties properties, RestTemplate template) {
        this.appInfo = queryInfo(template, properties.host(), properties.infoPath());
    }

    @Override
    public String getName() {
        return this.appInfo.getOrDefault("application", Collections.emptyMap()).get("name").toString();
    }

    @Override
    public String getVersion() {
        return this.appInfo.getOrDefault("application", Collections.emptyMap()).get("version").toString();
    }

    @Override
    public String getDescription() {
        return this.appInfo.getOrDefault("application", Collections.emptyMap()).get("description").toString();
    }
}
